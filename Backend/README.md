# sixseven (67) - Agentic Voice Backend

A FastAPI-based backend for voice-controlled research and creative tasks using a multi-agent architecture.

## Architecture

The backend implements an **agentic architecture** with specialized agents:

- **Orchestrator Agent**: Routes commands, manages job lifecycle, coordinates workflows
- **Dialogue/Response Agent**: Formats results into speakable summaries and structured JSON
- **Research Agent (Yutori)**: Creates and polls research tasks with structured output
- **Creative Agent (Freepik)**: Generates images using Reimagine Flux API
- **Status Agent**: Reports current system state for sessions
- **Cancellation Agent**: Handles cooperative job cancellation

## Quick Start

### 1. Install Dependencies

```bash
pip install -r requirements.txt
```

### 2. Configure Environment

```bash
cp .env.example .env
# Edit .env and add your API keys:
# YUTORI_API_KEY=your_key_here
# FREEPIK_API_KEY=your_key_here
```

### 3. Run Server

```bash
uvicorn app.main:app --reload
```

Server starts at `http://localhost:8000`

API docs available at `http://localhost:8000/docs`

## API Endpoints

### GET /healthz

Health check endpoint.

### GET /metrics

Prometheus metrics endpoint for monitoring.

### POST /v1/command

Main command endpoint - receives voice commands and orchestrates workflows.

**Request Body:**
```json
{
  "command_text": "research the latest AI trends",
  "image_base64": null,
  "session_id": "optional-session-id",
  "defaults": {
    "timezone": "America/Los_Angeles",
    "freepik_imagination": "vivid",
    "freepik_aspect_ratio": "original"
  }
}
```

**Response:**
```json
{
  "intent": "research",
  "message": "Starting research on: the latest AI trends...",
  "session_id": "abc-123",
  "job_id": "job-uuid",
  "status": "queued"
}
```

### GET /v1/jobs/{job_id}

Get full job details including results, events, and status.

### GET /v1/jobs

List jobs with optional filters: `session_id`, `type`, `status`, `limit`

### POST /v1/jobs/{job_id}/cancel

Cancel a running or queued job.

### GET /healthz

Health check endpoint.

## Supported Intents

### Research
```
"research the latest AI trends"
"research: quantum computing"
"research this: climate change solutions"
```

### Creative
```
"imagine a futuristic city"
"imagine: sunset over mountains"
"imagine this: abstract art"
```
*Requires `image_base64` in request*

### Status
```
"status"
```

### Stop/Cancel
```
"stop"
"cancel"
```

## curl Examples

### 1. Research Command

```bash
curl -X POST http://localhost:8000/v1/command \
  -H "Content-Type: application/json" \
  -d '{
    "command_text": "research the latest developments in quantum computing",
    "session_id": "session-123"
  }'
```

**Response:**
```json
{
  "intent": "research",
  "message": "Starting research on: the latest developments in quantum computing...",
  "session_id": "session-123",
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "queued"
}
```

### 2. Creative Command (with placeholder base64)

```bash
curl -X POST http://localhost:8000/v1/command \
  -H "Content-Type: application/json" \
  -d '{
    "command_text": "imagine a futuristic cityscape at sunset",
    "image_base64": "/9j/4AAQSkZJRgABAQEAYABgAAD...",
    "session_id": "session-123",
    "defaults": {
      "freepik_imagination": "vivid",
      "freepik_aspect_ratio": "16:9"
    }
  }'
```

**Response:**
```json
{
  "intent": "creative",
  "message": "Generating image: a futuristic cityscape at sunset...",
  "session_id": "session-123",
  "job_id": "660e8400-e29b-41d4-a716-446655440001",
  "status": "queued"
}
```

### 3. Status Command

```bash
curl -X POST http://localhost:8000/v1/command \
  -H "Content-Type: application/json" \
  -d '{
    "command_text": "status",
    "session_id": "session-123"
  }'
```

**Response:**
```json
{
  "intent": "status",
  "message": "Your research task is running. Elapsed time: 15 seconds.",
  "session_id": "session-123",
  "active_job": {
    "job_id": "550e8400-e29b-41d4-a716-446655440000",
    "type": "research",
    "status": "running",
    "elapsed_seconds": 15,
    "last_event": {
      "message": "Polling update: running",
      "ts": "2026-01-16T10:30:45.123456"
    }
  }
}
```

### 4. Stop Command

```bash
curl -X POST http://localhost:8000/v1/command \
  -H "Content-Type: application/json" \
  -d '{
    "command_text": "stop",
    "session_id": "session-123"
  }'
```

**Response:**
```json
{
  "intent": "stop",
  "message": "Task cancelled.",
  "session_id": "session-123",
  "cancelled_job_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 5. Fetch Job Details

```bash
curl http://localhost:8000/v1/jobs/550e8400-e29b-41d4-a716-446655440000
```

**Response:**
```json
{
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "session_id": "session-123",
  "type": "research",
  "status": "succeeded",
  "created_at": "2026-01-16T10:30:30.000000",
  "updated_at": "2026-01-16T10:31:00.000000",
  "input": {
    "command_text": "research the latest developments in quantum computing",
    "query_or_prompt": "the latest developments in quantum computing",
    "params": {"timezone": "America/Los_Angeles"},
    "image_present": false
  },
  "progress": 100,
  "result": {
    "task_id": "yutori-task-123",
    "view_url": "https://yutori.com/tasks/123",
    "structured_result": {
      "answer": "Recent quantum computing advances include...",
      "bullets": [
        "IBM achieved 127-qubit processor",
        "Google demonstrated quantum supremacy",
        "New error correction methods developed"
      ],
      "citations": [
        "https://example.com/source1",
        "https://example.com/source2"
      ]
    }
  },
  "events": [
    {
      "ts": "2026-01-16T10:30:30.000000",
      "level": "info",
      "message": "Research task started"
    },
    {
      "ts": "2026-01-16T10:30:35.000000",
      "level": "info",
      "message": "Research task created: yutori-task-123"
    },
    {
      "ts": "2026-01-16T10:31:00.000000",
      "level": "info",
      "message": "Research task succeeded"
    }
  ],
  "cancelled": false
}
```

### 6. List Jobs

```bash
# List all jobs
curl http://localhost:8000/v1/jobs

# Filter by session
curl "http://localhost:8000/v1/jobs?session_id=session-123"

# Filter by type and status
curl "http://localhost:8000/v1/jobs?type=research&status=succeeded&limit=10"
```

### 7. Cancel Job

```bash
curl -X POST http://localhost:8000/v1/jobs/550e8400-e29b-41d4-a716-446655440000/cancel
```

**Response:**
```json
{
  "success": true,
  "message": "Job cancelled",
  "job_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 8. Health Check

```bash
curl http://localhost:8000/healthz
```

**Response:**
```json
{
  "ok": true
}
```

## Job State Model

### Job Statuses
- `queued`: Job created, waiting to execute
- `running`: Job actively executing
- `succeeded`: Job completed successfully
- `failed`: Job encountered an error
- `cancelled`: Job was cancelled by user

### Job Fields
- `job_id`: Unique identifier
- `session_id`: Optional session grouping
- `type`: "research" or "creative"
- `status`: Current job status
- `input`: Original command and parameters
- `progress`: Optional 0-100 percentage
- `result`: Structured output from agents
- `error`: Error details if failed
- `events`: Timeline of job execution (capped at 50)
- `cancelled`: Cancellation flag for cooperative shutdown

## External Integrations

### Yutori Research API
- Creates research tasks with structured output schema
- Polls every 2-3 seconds until completion
- Returns: answer, bullets, citations, view_url

### Freepik Reimagine Flux API
- Generates images from base64 input + prompt
- Supports imagination and aspect_ratio parameters
- Returns: generated URLs or async task status

## Development

### Observability

The backend includes comprehensive observability features:

- **Structured Logging**: JSON logs with automatic context (request_id, session_id, job_id)
- **Distributed Tracing**: OpenTelemetry spans for request flows
- **Prometheus Metrics**: Counters, histograms, and gauges for monitoring

See [OBSERVABILITY.md](OBSERVABILITY.md) for detailed documentation.

**Key metrics available at `/metrics`:**
- Command counts by intent and status
- Job duration histograms
- Active job gauges
- API latency distributions
- External API call tracking

**Example log output:**
```json
{
  "event": "job_completed",
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "job_type": "research",
  "status": "succeeded",
  "duration_seconds": 15.3,
  "request_id": "abc-123",
  "timestamp": "2026-01-16T10:30:45.123456Z"
}
```

### Project Structure
```
.
├── app/
│   ├── __init__.py
│   ├── main.py              # FastAPI app and routes
│   ├── models.py            # Pydantic models
│   ├── store.py             # Job storage (in-memory)
│   ├── orchestrator.py      # Orchestrator agent
│   ├── observability.py     # Logging, tracing, metrics
│   ├── agents/
│   │   ├── __init__.py
│   │   ├── dialogue.py      # Response formatting
│   │   ├── research.py      # Yutori integration
│   │   ├── creative.py      # Freepik integration
│   │   ├── status.py        # Status reporting
│   │   └── cancel.py        # Job cancellation
│   └── utils/
│       ├── __init__.py
│       └── http.py          # HTTP helpers with retry
├── requirements.txt
├── .env.example
├── README.md
└── OBSERVABILITY.md         # Observability guide
```

### Running Tests

The backend is designed to be testable. Each agent is a separate module with clear responsibilities.

### Logging

Structured logging includes:
- Request/response details
- Job lifecycle events
- Agent execution traces
- Error details with safe truncation

## Production Considerations

For production deployment:

1. **Replace InMemoryJobStore** with persistent storage (PostgreSQL, Redis)
2. **Add authentication** and rate limiting
3. **Use task queue** (Celery, RQ) for job execution
4. **Add monitoring** and metrics (Prometheus, DataDog)
5. **Implement job TTL** and cleanup
6. **Add request validation** for image size limits
7. **Configure CORS** appropriately
8. **Use environment-based config** for different stages

## License

MIT
