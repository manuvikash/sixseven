# Agent Guidelines for sixseven (67)

This document provides coding guidelines for AI agents working on the sixseven voice-controlled agentic backend.

## Build, Test, and Run Commands

### Running the Server
```bash
# Development mode with auto-reload
uvicorn app.main:app --reload

# Production mode
uvicorn app.main:app --host 0.0.0.0 --port 8000

# Using the startup script
./run.sh
```

### Testing
```bash
# Run the comprehensive API test suite
./test_api.sh

# Run a single Python test file
python test_creative_real.py

# Run a specific Python test function (if using pytest)
pytest test_creative_real.py::test_function_name -v

# Quick manual testing with curl
curl http://localhost:8000/healthz
```

### Environment Setup
```bash
# Create virtual environment
python3 -m venv venv

# Activate virtual environment
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Setup environment variables
cp .env.example .env
# Edit .env with your API keys
```

## Project Architecture

### Structure Overview
```
app/
├── main.py              # FastAPI app, routes, middleware
├── models.py            # Pydantic models (Job, Session, Request/Response)
├── store.py             # JobStore interface + InMemoryJobStore
├── orchestrator.py      # OrchestratorAgent (routes commands)
├── observability.py     # Logging, tracing, metrics
├── agents/
│   ├── dialogue.py      # Response formatting
│   ├── research.py      # Yutori API integration
│   ├── creative.py      # Freepik API integration
│   ├── status.py        # Session status queries
│   └── cancel.py        # Job cancellation
└── utils/
    └── http.py          # HTTP helpers with retry logic
```

### Key Concepts
- **Agents**: Specialized modules with single responsibilities (research, creative, status, cancel, dialogue)
- **Jobs**: Async background tasks with lifecycle states (queued → running → succeeded/failed/cancelled)
- **Sessions**: Group related commands in a conversation context
- **Store**: Abstract storage interface (currently in-memory, swappable with PostgreSQL/Redis)

## Code Style Guidelines

### Imports
Follow this import order (PEP 8):
1. Standard library imports
2. Third-party library imports (FastAPI, Pydantic, httpx, etc.)
3. Local application imports (app.models, app.store, etc.)

```python
# Standard library
import asyncio
import os
from typing import Dict, Any, Optional

# Third-party
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

# Local
from app.models import Job, Session
from app.store import JobStore
from app.observability import get_logger
```

### Formatting
- **Line length**: Aim for 100 characters or less
- **Indentation**: 4 spaces (no tabs)
- **Quotes**: Use double quotes for strings
- **Trailing commas**: Use them in multi-line collections

### Type Hints
Always use type hints for function parameters and return values:

```python
async def execute(self, job: Job, timezone: str = "America/Los_Angeles") -> None:
    """Execute research workflow."""
    pass

def _parse_intent(self, command_text: str) -> tuple[str, Dict[str, Any]]:
    """Parse command text to determine intent."""
    pass
```

### Naming Conventions
- **Classes**: `PascalCase` (e.g., `OrchestratorAgent`, `JobStore`)
- **Functions/Methods**: `snake_case` (e.g., `handle_command`, `get_status`)
- **Private methods**: Prefix with `_` (e.g., `_parse_intent`, `_create_task`)
- **Constants**: `UPPER_SNAKE_CASE` (e.g., `BASE_URL`, `MAX_RETRIES`)
- **Variables**: `snake_case` (e.g., `job_id`, `session_id`)

### Pydantic Models
Use Pydantic for all data models with proper defaults:

```python
class Job(BaseModel):
    job_id: str = Field(default_factory=lambda: str(uuid4()))
    session_id: Optional[str] = None
    type: Literal["research", "creative"]
    status: Literal["queued", "running", "succeeded", "failed", "cancelled"] = "queued"
    created_at: datetime = Field(default_factory=datetime.utcnow)
    events: List[JobEvent] = Field(default_factory=list)
```

### Async/Await
- Use `async def` for all I/O operations (HTTP requests, database queries)
- Use `await` for async function calls
- Use `asyncio.create_task()` for background tasks (not `await`)

```python
# Background task execution
asyncio.create_task(self._execute_research(job, timezone))

# Await for immediate results
result = await http_post_with_retry(url, headers, payload)
```

### Error Handling
- Use structured logging with context (job_id, session_id)
- Return error dictionaries with `error: True` flag for external API calls
- Use try/except at agent execution boundaries

```python
try:
    result = await self._create_task(query, timezone)
    if result.get("error"):
        job.status = "failed"
        job.error = {"message": "Failed to create task", "details": result.get("message")}
        self.store.update_job(job)
        return
except Exception as e:
    logger.error("research_error", job_id=job.job_id, error=str(e), exc_info=True)
    job.status = "failed"
    job.error = {"message": str(e)}
    self.store.update_job(job)
```

### Logging
Use structured logging with the observability module:

```python
from app.observability import get_logger

logger = get_logger(__name__)

# Good: Structured with context
logger.info("research_started", job_id=job.job_id, query=query[:100])

# Good: Error with exception info
logger.error("research_failed", job_id=job.job_id, error=str(e), exc_info=True)

# Avoid: String formatting
logger.info(f"Research started for job {job_id}")  # Don't do this
```

### Job Lifecycle Management
Always update job status and events:

```python
# Starting a job
job.status = "running"
job.add_event("info", "Task started")
self.store.update_job(job)

# Completing successfully
job.status = "succeeded"
job.progress = 100
job.result = {"task_id": task_id, "data": result}
job.add_event("info", "Task completed")
self.store.update_job(job)

# Handling failures
job.status = "failed"
job.error = {"message": "Error description", "details": details}
job.add_event("error", "Task failed", {"error": str(e)})
self.store.update_job(job)
```

### Cooperative Cancellation
Check `job.cancelled` flag in polling loops:

```python
while not job.cancelled:
    await asyncio.sleep(2.5)
    result = await self._poll_task(task_id)
    
    if result.get("status") == "succeeded":
        break

if job.cancelled:
    job.status = "cancelled"
    job.add_event("info", "Task cancelled")
    return
```

## Testing Guidelines

- Test files should be named `test_*.py`
- Use descriptive test names that explain what is being tested
- Test API endpoints using httpx.AsyncClient
- Mock external API calls for unit tests (Yutori, Freepik)
- Use the `test_api.sh` script for end-to-end testing

## Common Patterns

### Agent Structure
```python
class MyAgent:
    def __init__(self, store: JobStore, observer=None):
        self.store = store
        self.observer = observer
    
    async def execute(self, job: Job, **params):
        job.status = "running"
        job.add_event("info", "Started")
        self.store.update_job(job)
        
        try:
            result = await self._do_work()
            job.status = "succeeded"
            job.result = result
        except Exception as e:
            job.status = "failed"
            job.error = {"message": str(e)}
        
        job.add_event("info", f"Completed: {job.status}")
        self.store.update_job(job)
```

### HTTP Retry Pattern
```python
from app.utils.http import http_post_with_retry, http_get_with_retry

result = await http_post_with_retry(url, headers, payload, timeout=30.0, max_retries=2)
if result.get("error"):
    # Handle error
    pass
```

## External API Integration

### Yutori Research API
- Endpoint: `https://api.yutori.com/v1/research/tasks`
- Auth: `X-API-Key` header
- Pattern: Create task → Poll until complete (2.5s intervals)

### Freepik Reimagine Flux API
- Endpoint: `https://api.freepik.com/v1/ai/text-to-image/seedream-v4-5-edit`
- Auth: `x-freepik-api-key` header
- Input: base64 image + prompt

## Documentation

- Update README.md for user-facing changes
- Update API_SPEC.md for API changes
- Update ARCHITECTURE.md for architectural changes
- Add docstrings to all public functions and classes
