# SixSeven (67) API Specification

**Base URL:** `http://localhost:8000`

**Version:** 1.0.0

**Description:** Voice-controlled agentic backend for research and creative tasks

---

## Table of Contents

1. [Authentication](#authentication)
2. [Core Concepts](#core-concepts)
3. [Endpoints](#endpoints)
4. [Data Models](#data-models)
5. [Error Handling](#error-handling)
6. [Examples](#examples)

---

## Authentication

Currently no authentication required for local development. For production, implement API key authentication via headers.

---

## Core Concepts

### Sessions
- Group related commands into a conversation
- Use `session_id` to maintain context across multiple commands
- Optional - system generates one if not provided

### Jobs
- Represent asynchronous tasks (research or creative)
- Have lifecycle: `queued` → `running` → `succeeded`/`failed`/`cancelled`
- Can be queried for status and results
- Emit events during execution for tracking

### Intents
- Parsed from natural language commands
- Supported: `research`, `creative`, `status`, `stop`, `unknown`

---

## Endpoints

### 1. Send Command

**POST** `/v1/command`

Main endpoint for voice commands. Returns immediately with job ID for async tasks.

#### Request Body

```json
{
  "command_text": "research quantum computing",
  "image_base64": null,
  "session_id": "optional-session-id",
  "defaults": {
    "timezone": "America/Los_Angeles",
    "freepik_imagination": "vivid",
    "freepik_aspect_ratio": "16:9"
  }
}
```

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `command_text` | string | Yes | Natural language command (everything after "hey 67") |
| `image_base64` | string | No | Base64-encoded image for creative tasks (JPEG/PNG) |
| `session_id` | string | No | Session identifier for grouping commands |
| `defaults` | object | No | Default parameters for tasks |
| `defaults.timezone` | string | No | User timezone (default: "America/Los_Angeles") |
| `defaults.freepik_imagination` | string | No | Freepik imagination level (default: "vivid") |
| `defaults.freepik_aspect_ratio` | string | No | Image aspect ratio (default: "16:9") |

#### Response

```json
{
  "intent": "research",
  "message": "Starting research on: quantum computing...",
  "session_id": "abc-123",
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "queued",
  "active_job": null,
  "cancelled_job_id": null
}
```

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `intent` | string | Detected intent: "research", "creative", "status", "stop", "unknown" |
| `message` | string | Human-friendly message suitable for TTS |
| `session_id` | string | Session ID (echoed or generated) |
| `job_id` | string | UUID of created job (for research/creative) |
| `status` | string | Initial job status (for research/creative) |
| `active_job` | object | Active job summary (for status intent) |
| `cancelled_job_id` | string | ID of cancelled job (for stop intent) |

#### Status Codes

- `200 OK` - Command processed successfully
- `500 Internal Server Error` - Server error

---

### 2. Get Job Details

**GET** `/v1/jobs/{job_id}`

Retrieve complete job information including results and events.

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `job_id` | string | Yes | UUID of the job |

#### Response

```json
{
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "session_id": "abc-123",
  "type": "research",
  "status": "succeeded",
  "created_at": "2026-01-16T10:30:00.000000",
  "updated_at": "2026-01-16T10:30:15.000000",
  "input": {
    "command_text": "research quantum computing",
    "query_or_prompt": "quantum computing",
    "params": {
      "timezone": "America/Los_Angeles"
    },
    "image_present": false
  },
  "progress": 100,
  "result": {
    "task_id": "yutori-task-123",
    "view_url": "https://scouts.yutori.com/...",
    "structured_result": {
      "answer": "Quantum computing has...",
      "bullets": [
        "Key point 1",
        "Key point 2"
      ],
      "citations": [
        "https://example.com/source1",
        "https://example.com/source2"
      ]
    },
    "markdown_result": "# Full Report..."
  },
  "error": null,
  "events": [
    {
      "ts": "2026-01-16T10:30:00.000000",
      "level": "info",
      "message": "Research task started",
      "data": null
    }
  ],
  "cancelled": false
}
```

#### Status Codes

- `200 OK` - Job found
- `404 Not Found` - Job not found

---

### 3. List Jobs

**GET** `/v1/jobs`

List jobs with optional filtering.

#### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `session_id` | string | No | Filter by session |
| `type` | string | No | Filter by type: "research" or "creative" |
| `status` | string | No | Filter by status: "queued", "running", "succeeded", "failed", "cancelled" |
| `limit` | integer | No | Max results (1-100, default: 20) |

#### Response

```json
[
  {
    "job_id": "...",
    "session_id": "...",
    "type": "research",
    "status": "succeeded",
    ...
  }
]
```

Returns array of job objects (newest first).

#### Status Codes

- `200 OK` - Success

---

### 4. Cancel Job

**POST** `/v1/jobs/{job_id}/cancel`

Cancel a running or queued job.

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `job_id` | string | Yes | UUID of the job to cancel |

#### Response

```json
{
  "success": true,
  "message": "Job cancelled",
  "job_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### Status Codes

- `200 OK` - Job cancelled or already terminal
- `404 Not Found` - Job not found

---

### 5. Health Check

**GET** `/healthz`

Check if backend is running.

#### Response

```json
{
  "ok": true
}
```

#### Status Codes

- `200 OK` - Backend is healthy

---

### 6. Metrics

**GET** `/metrics`

Prometheus-formatted metrics for monitoring.

#### Response

```
# HELP sixseven_commands_total Total number of commands received
# TYPE sixseven_commands_total counter
sixseven_commands_total{intent="research",status="created"} 42.0
...
```

#### Status Codes

- `200 OK` - Metrics available

---

## Data Models

### Job Object

```typescript
interface Job {
  job_id: string;              // UUID
  session_id: string | null;   // Optional session grouping
  type: "research" | "creative";
  status: "queued" | "running" | "succeeded" | "failed" | "cancelled";
  created_at: string;          // ISO 8601 timestamp
  updated_at: string;          // ISO 8601 timestamp
  input: JobInput;
  progress: number | null;     // 0-100 percentage
  result: JobResult | null;
  error: JobError | null;
  events: JobEvent[];          // Max 50 events
  cancelled: boolean;
}
```

### JobInput Object

```typescript
interface JobInput {
  command_text: string;
  query_or_prompt: string;
  params: Record<string, any>;
  image_present: boolean;
}
```

### JobResult Object (Research)

```typescript
interface ResearchResult {
  task_id: string;
  view_url: string;
  structured_result: {
    answer: string;
    bullets: string[];
    citations: string[];
  };
  markdown_result?: string;
}
```

### JobResult Object (Creative)

```typescript
interface CreativeResult {
  task_id: string;
  status: string;
  generated_urls: string[];
  async_note?: string;
  full_response: any;
}
```

### JobError Object

```typescript
interface JobError {
  message: string;
  details?: string;
  payload?: string;
}
```

### JobEvent Object

```typescript
interface JobEvent {
  ts: string;                  // ISO 8601 timestamp
  level: "info" | "warning" | "error";
  message: string;
  data?: Record<string, any>;
}
```

---

## Error Handling

### Error Response Format

```json
{
  "detail": "Error message here"
}
```

### Common Error Scenarios

1. **Invalid Command**
   - Intent: `unknown`
   - Message: "I didn't understand that command..."

2. **Missing Image for Creative**
   - Intent: `creative`
   - Message: "Please provide an image for creative tasks."

3. **Job Not Found**
   - Status: `404`
   - Detail: "Job not found"

4. **External API Failure**
   - Job status: `failed`
   - Error object contains details

---

## Examples

### Example 1: Research Flow

```bash
# 1. Send research command
curl -X POST http://localhost:8000/v1/command \
  -H "Content-Type: application/json" \
  -d '{
    "command_text": "research latest AI developments",
    "session_id": "user-123"
  }'

# Response:
{
  "intent": "research",
  "message": "Starting research on: latest AI developments...",
  "session_id": "user-123",
  "job_id": "abc-def-123",
  "status": "queued"
}

# 2. Poll for results (every 2-3 seconds)
curl http://localhost:8000/v1/jobs/abc-def-123

# 3. When status = "succeeded", extract result
{
  "status": "succeeded",
  "result": {
    "structured_result": {
      "answer": "Recent AI developments include...",
      "bullets": ["Point 1", "Point 2"],
      "citations": ["url1", "url2"]
    }
  }
}
```

### Example 2: Creative Flow

```bash
# 1. Convert image to base64
IMAGE_BASE64=$(base64 -w 0 image.jpg)

# 2. Send creative command
curl -X POST http://localhost:8000/v1/command \
  -H "Content-Type: application/json" \
  -d "{
    \"command_text\": \"imagine a futuristic city\",
    \"image_base64\": \"$IMAGE_BASE64\",
    \"session_id\": \"user-123\",
    \"defaults\": {
      \"freepik_aspect_ratio\": \"16:9\"
    }
  }"

# Response:
{
  "intent": "creative",
  "message": "Generating image: a futuristic city...",
  "job_id": "xyz-789",
  "status": "queued"
}

# 3. Poll for results
curl http://localhost:8000/v1/jobs/xyz-789

# 4. Extract generated images
{
  "status": "succeeded",
  "result": {
    "generated_urls": [
      "https://cdn.freepik.com/generated/image1.jpg"
    ]
  }
}
```

### Example 3: Status Check

```bash
curl -X POST http://localhost:8000/v1/command \
  -H "Content-Type: application/json" \
  -d '{
    "command_text": "status",
    "session_id": "user-123"
  }'

# Response:
{
  "intent": "status",
  "message": "Your research task is running. Elapsed time: 5 seconds.",
  "active_job": {
    "job_id": "abc-def-123",
    "type": "research",
    "status": "running",
    "elapsed_seconds": 5
  }
}
```

### Example 4: Cancel Job

```bash
# Via stop command
curl -X POST http://localhost:8000/v1/command \
  -H "Content-Type: application/json" \
  -d '{
    "command_text": "stop",
    "session_id": "user-123"
  }'

# Or directly
curl -X POST http://localhost:8000/v1/jobs/abc-def-123/cancel
```

---

## Frontend Integration Guide

### Recommended Flow

1. **Send Command**
   - POST to `/v1/command` with user's voice input
   - Speak the `message` field via TTS
   - Store `job_id` if present

2. **Poll for Results** (if job created)
   - GET `/v1/jobs/{job_id}` every 2-3 seconds
   - Check `status` field
   - Stop polling when status is terminal (`succeeded`, `failed`, `cancelled`)

3. **Display Results**
   - **Research**: Show `answer`, `bullets`, `citations`
   - **Creative**: Display images from `generated_urls`
   - **Error**: Show `error.message`

4. **Session Management**
   - Generate `session_id` per conversation/call
   - Include in all commands for context
   - Use for status checks and cancellation

### WebSocket Alternative (Future)

For real-time updates without polling, consider implementing WebSocket endpoint:
- Subscribe to job updates
- Receive events as they happen
- No polling needed

### Response Headers

All responses include:
- `X-Request-ID`: Unique request identifier for debugging
- `Content-Type`: `application/json`

### Rate Limiting (Production)

Implement rate limiting on frontend:
- Max 10 commands per minute per session
- Max 1 command per second

---

## Testing

### Interactive API Docs

Visit `http://localhost:8000/docs` for Swagger UI with:
- All endpoints documented
- Try-it-out functionality
- Request/response examples

### Dashboard

Open `dashboard.html` in browser for:
- Visual job monitoring
- Test commands
- Real-time metrics
- Job result viewing

---

## Support

For issues or questions:
- Check server logs for detailed error traces
- Use `/metrics` endpoint for system health
- Review job `events` array for execution timeline
- Check `X-Request-ID` header for request tracing

---

## Changelog

### v1.0.0 (2026-01-16)
- Initial API release
- Research agent (Yutori integration)
- Creative agent (Freepik Seedream 4.5)
- Job management and cancellation
- Session support
- Observability (metrics, logs, traces)
