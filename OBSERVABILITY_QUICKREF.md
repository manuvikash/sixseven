# Observability Quick Reference

## Endpoints

```bash
# Health check
curl http://localhost:8000/healthz

# Prometheus metrics
curl http://localhost:8000/metrics
```

## Key Metrics

| Metric | Type | Purpose |
|--------|------|---------|
| `sixseven_commands_total` | Counter | Track commands by intent/status |
| `sixseven_job_duration_seconds` | Histogram | Job execution time |
| `sixseven_active_jobs` | Gauge | Currently running jobs |
| `sixseven_api_latency_seconds` | Histogram | API endpoint response time |
| `sixseven_external_api_calls_total` | Counter | External API calls |
| `sixseven_external_api_latency_seconds` | Histogram | External API response time |

## Log Events

### API Layer
- `command_received` - New command
- `command_handled` - Success
- `command_error` - Failure
- `job_cancelled` - Cancellation

### Job Lifecycle
- `job_created` - Job created
- `job_started` - Execution started
- `job_progress` - Progress update
- `job_completed` - Finished (success/fail)

### Research Agent
- `research_started` - Started
- `research_task_created` - Yutori task created
- `research_succeeded` - Completed
- `research_failed` - Failed

### Creative Agent
- `creative_started` - Started
- `creative_succeeded` - Completed
- `creative_failed` - Failed

### External APIs
- `external_api_call` - API called

## Context Variables

Every log includes:
- `request_id` - Unique per request
- `session_id` - User session
- `job_id` - Background job
- `timestamp` - ISO 8601

## Useful Queries

### Prometheus

```promql
# Request rate
rate(sixseven_commands_total[5m])

# Success rate
sum(rate(sixseven_commands_total{status="succeeded"}[5m])) /
sum(rate(sixseven_commands_total[5m]))

# P95 latency
histogram_quantile(0.95, rate(sixseven_api_latency_seconds_bucket[5m]))

# Active jobs
sixseven_active_jobs

# External API errors
rate(sixseven_external_api_calls_total{status="error"}[5m])
```

### Log Queries (Kibana/Datadog)

```
# All errors
level: "error"

# Specific job
job_id: "550e8400-e29b-41d4-a716-446655440000"

# Slow operations
duration_seconds: >10

# Failed external calls
event: "external_api_call" AND success: false

# Research failures
event: "research_failed"
```

## Alerts

```yaml
# High error rate
expr: sum(rate(sixseven_commands_total{status="failed"}[5m])) / sum(rate(sixseven_commands_total[5m])) > 0.1

# Slow API
expr: histogram_quantile(0.95, rate(sixseven_api_latency_seconds_bucket[5m])) > 5

# External API failures
expr: rate(sixseven_external_api_calls_total{status="error"}[5m]) > 0.5

# Stuck jobs
expr: sixseven_active_jobs > 10 for 30m
```

## Code Examples

### Structured Logging

```python
from app.observability import get_logger

logger = get_logger(__name__)

# Simple
logger.info("operation_completed")

# With data
logger.info("user_action", user_id="123", action="create")

# Error
logger.error("operation_failed", error=str(e), exc_info=True)
```

### Using Observer

```python
# In agent
if self.observer:
    self.observer.job_created(job)
    self.observer.job_started(job)
    self.observer.job_progress(job, 50, "Halfway")
    self.observer.external_api_call("yutori", "create", 1.5, True)
    self.observer.job_completed(job)
```

### Tracing

```python
from opentelemetry import trace

tracer = trace.get_tracer(__name__)

with tracer.start_as_current_span("operation") as span:
    span.set_attribute("param", value)
    # do work
```

## Troubleshooting

| Issue | Check |
|-------|-------|
| No logs | Verify structlog setup |
| No metrics | Ensure observer passed to agents |
| No traces | Check tracer initialization |
| Missing context | Verify context vars set |

## Production Setup

1. Change trace exporter from Console to OTLP
2. Configure Prometheus scraping
3. Ship logs to ELK/Datadog
4. Create Grafana dashboards
5. Set up alerts
6. Enable log rotation
7. Configure trace sampling

## Resources

- Full guide: [OBSERVABILITY.md](OBSERVABILITY.md)
- Implementation: [OBSERVABILITY_SUMMARY.md](OBSERVABILITY_SUMMARY.md)
- Architecture: [ARCHITECTURE.md](ARCHITECTURE.md)
