# Architecture Documentation

## Multi-Agent System Design

The sixseven (67) backend implements a **multi-agent architecture** where each agent has a single, well-defined responsibility. This design enables:

- **Modularity**: Each agent can be tested and modified independently
- **Scalability**: Agents can be distributed across services
- **Maintainability**: Clear boundaries and responsibilities
- **Extensibility**: New agents can be added without modifying existing ones

## Agent Responsibilities

### 1. Orchestrator Agent (`app/orchestrator.py`)

**Role**: Central coordinator and traffic controller

**Responsibilities**:
- Receives all incoming commands
- Parses intent from natural language
- Routes to appropriate workflow agents
- Manages job lifecycle (create, update, track)
- Manages session state
- Coordinates async execution
- Returns immediate responses to callers

**State Machine**:
```
Command → Parse Intent → Route to Agent → Create Job → Background Execution
                                              ↓
                                         Update Status
                                              ↓
                                    [queued → running → succeeded/failed/cancelled]
```

### 2. Dialogue/Response Agent (`app/agents/dialogue.py`)

**Role**: Response formatter and speech synthesizer

**Responsibilities**:
- Converts internal job results into speakable summaries
- Formats structured JSON for UI consumption
- Ensures responses are concise (1-2 sentences + up to 3 bullets)
- Maintains consistent message formatting
- Handles error message formatting

**Output Format**:
```python
{
    "speakable": "Short summary. Key points: - Point 1 - Point 2 - Point 3",
    "structured": {
        # Full structured data for UI
    }
}
```

### 3. Research Agent (`app/agents/research.py`)

**Role**: Yutori research integration specialist

**Responsibilities**:
- Creates Yutori research tasks with structured output schema
- Polls task status every 2-3 seconds
- Emits progress events (queued, running, elapsed time)
- Handles cooperative cancellation (checks `job.cancelled` flag)
- Extracts and structures results (answer, bullets, citations)
- Manages error states and retries

**Workflow**:
```
1. Create Yutori task with output_schema
2. Poll GET /tasks/{id} every 2.5s
3. Check job.cancelled flag each iteration
4. On success: extract structured_result
5. Update job with result/error
```

### 4. Creative Agent (`app/agents/creative.py`)

**Role**: Freepik image generation specialist

**Responsibilities**:
- Validates image_base64 presence
- Calls Freepik Reimagine Flux API
- Handles both sync and async responses
- Extracts generated image URLs
- Stores full response payload for debugging
- Manages API-specific error states

**Workflow**:
```
1. Validate image_base64 exists
2. POST to Freepik with image + prompt + params
3. Check response status (CREATED/IN_PROGRESS/COMPLETED)
4. Extract URLs or store async state
5. Update job with result
```

### 5. Status Agent (`app/agents/status.py`)

**Role**: System state reporter

**Responsibilities**:
- Queries active job for a session
- Calculates elapsed time
- Formats current state into speakable message
- Returns structured job summary
- Handles "no active job" cases

**Output**:
```python
{
    "active_job": {
        "job_id": "...",
        "type": "research",
        "status": "running",
        "elapsed_seconds": 15,
        "last_event": {...}
    },
    "message": "Your research task is running. Elapsed time: 15 seconds."
}
```

### 6. Cancellation Agent (`app/agents/cancel.py`)

**Role**: Job cancellation coordinator

**Responsibilities**:
- Finds active job for session (or globally)
- Sets `job.cancelled = True` flag
- Updates job status to "cancelled"
- Emits cancellation event
- Ensures cooperative cancellation (agents check flag)

**Cooperative Cancellation**:
```python
# In polling loops:
while not job.cancelled:
    # Do work
    await asyncio.sleep(2.5)
    # Check again
```

## Data Flow

### Research Command Flow
```
1. POST /v1/command {"command_text": "research X"}
2. Orchestrator.handle_command()
   ├─ Parse intent → "research"
   ├─ Create Job (status: queued)
   ├─ Update Session (active_job_id)
   └─ Return immediate response
3. Background: ResearchAgent.execute()
   ├─ Update status → "running"
   ├─ Create Yutori task
   ├─ Poll until complete (check cancelled flag)
   ├─ Extract structured result
   └─ Update status → "succeeded"
4. GET /v1/jobs/{id} → Full job with results
5. DialogueAgent.format_research_result() → Speakable summary
```

### Creative Command Flow
```
1. POST /v1/command {"command_text": "imagine X", "image_base64": "..."}
2. Orchestrator.handle_command()
   ├─ Parse intent → "creative"
   ├─ Validate image_base64
   ├─ Create Job (status: queued)
   └─ Return immediate response
3. Background: CreativeAgent.execute()
   ├─ Update status → "running"
   ├─ POST to Freepik API
   ├─ Extract URLs or async state
   └─ Update status → "succeeded"
4. GET /v1/jobs/{id} → Full job with image URLs
```

### Status Query Flow
```
1. POST /v1/command {"command_text": "status"}
2. Orchestrator.handle_command()
   └─ Route to StatusAgent
3. StatusAgent.get_status()
   ├─ Get session.active_job_id
   ├─ Get job details
   ├─ Calculate elapsed time
   └─ Format speakable message
4. Return immediate response with active_job summary
```

### Cancellation Flow
```
1. POST /v1/command {"command_text": "stop"}
2. Orchestrator.handle_command()
   └─ Route to CancellationAgent
3. CancellationAgent.cancel_job()
   ├─ Find active job for session
   ├─ Set job.cancelled = True
   ├─ Update status → "cancelled"
   └─ Emit event
4. Running agent checks job.cancelled
   └─ Exits polling loop
5. Return response with cancelled_job_id
```

## State Management

### Job State Model
```python
Job:
  - job_id: UUID
  - session_id: Optional[str]
  - type: "research" | "creative"
  - status: "queued" | "running" | "succeeded" | "failed" | "cancelled"
  - created_at, updated_at: datetime
  - input: {command_text, query_or_prompt, params, image_present}
  - progress: Optional[int] (0-100)
  - result: Optional[dict] (provider results + structured outputs)
  - error: Optional[dict] (safe error + payload excerpts)
  - events: List[JobEvent] (capped at 50)
  - cancelled: bool (cooperative cancellation flag)
```

### Session State Model
```python
Session:
  - session_id: str
  - active_job_id: Optional[str]
  - last_command_text: Optional[str]
  - last_intent: Optional[str]
  - last_updated_at: datetime
```

### Event Model
```python
JobEvent:
  - ts: datetime
  - level: "info" | "warning" | "error"
  - message: str
  - data: Optional[dict]
```

## Storage Layer

### JobStore Interface
```python
class JobStore:
    def create_job(job: Job) -> Job
    def get_job(job_id: str) -> Optional[Job]
    def update_job(job: Job) -> Job
    def list_jobs(...filters...) -> List[Job]
    def get_session(session_id: str) -> Optional[Session]
    def update_session(session: Session) -> Session
```

### InMemoryJobStore Implementation
- Thread-safe with locks
- In-memory dictionaries for jobs and sessions
- Suitable for development and testing
- Should be replaced with persistent storage for production

## External Integration Patterns

### Yutori Research API
```python
# Create task
POST https://api.yutori.com/v1/research/tasks
{
    "query": "...",
    "user_timezone": "America/Los_Angeles",
    "task_spec": {
        "output_schema": {
            "type": "object",
            "properties": {
                "answer": {"type": "string"},
                "bullets": {"type": "array", "items": {"type": "string"}},
                "citations": {"type": "array", "items": {"type": "string"}}
            }
        }
    }
}

# Poll task
GET https://api.yutori.com/v1/research/tasks/{task_id}
# Repeat every 2-3 seconds until status = "succeeded" or "failed"
```

### Freepik Reimagine Flux API
```python
POST https://api.freepik.com/v1/ai/beta/text-to-image/reimagine-flux
{
    "image": "base64_string",
    "prompt": "...",
    "imagination": "vivid",
    "aspect_ratio": "original"
}

# Response may be:
# - Synchronous: immediate URLs
# - Asynchronous: status CREATED/IN_PROGRESS (store payload)
```

## Error Handling Strategy

### Safe Error Payloads
- Truncate long responses to 500 chars
- Store status codes and error messages
- Include partial data for debugging
- Never expose sensitive information

### Retry Logic
- HTTP requests: 2 retries with exponential backoff
- Timeout: 30s for most requests, 60s for image generation
- Graceful degradation on failures

### Event Logging
- All state transitions logged as events
- Events capped at 50 per job
- Include timestamps and structured data
- Levels: info, warning, error

## Scalability Considerations

### Current Design (Single Process)
- In-memory storage
- Background tasks via asyncio.create_task()
- Suitable for: demos, prototypes, low traffic

### Production Scaling Path
1. **Storage**: Replace InMemoryJobStore with PostgreSQL/Redis
2. **Task Queue**: Use Celery/RQ for job execution
3. **Horizontal Scaling**: Multiple API servers + shared storage
4. **Caching**: Redis for session state
5. **Monitoring**: Prometheus metrics, structured logging
6. **Rate Limiting**: Per-session and global limits

## Testing Strategy

### Unit Tests
- Each agent independently testable
- Mock external APIs (Yutori, Freepik)
- Test state transitions
- Test error handling

### Integration Tests
- End-to-end command flows
- Session management
- Cancellation behavior
- Concurrent job execution

### Load Tests
- Multiple concurrent sessions
- Job queue behavior
- Memory usage under load
- API response times

## Extension Points

### Adding New Agents
1. Create new agent class in `app/agents/`
2. Implement execute() method
3. Add to Orchestrator initialization
4. Add routing logic in handle_command()
5. Update models if needed

### Adding New Intents
1. Add parsing logic in Orchestrator._parse_intent()
2. Create handler method (e.g., _handle_new_intent)
3. Add to routing in handle_command()
4. Update CommandResponse model

### Adding New Providers
1. Create new agent (e.g., SearchAgent)
2. Implement provider-specific API calls
3. Add to utils/http.py if needed
4. Update Job.type enum if needed
