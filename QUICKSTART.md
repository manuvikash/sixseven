# Quick Start Guide

## Setup (30 seconds)

```bash
# 1. Install dependencies
pip install -r requirements.txt

# 2. Configure API keys
cp .env.example .env
# Edit .env and add your YUTORI_API_KEY and FREEPIK_API_KEY

# 3. Start server
uvicorn app.main:app --reload
```

Server runs at: http://localhost:8000

## Test It

### Research Command
```bash
curl -X POST http://localhost:8000/v1/command \
  -H "Content-Type: application/json" \
  -d '{"command_text": "research quantum computing", "session_id": "test-1"}'
```

### Check Status
```bash
curl -X POST http://localhost:8000/v1/command \
  -H "Content-Type: application/json" \
  -d '{"command_text": "status", "session_id": "test-1"}'
```

### Get Job Details
```bash
# Replace JOB_ID with the job_id from the research response
curl http://localhost:8000/v1/jobs/JOB_ID
```

### Creative Command
```bash
curl -X POST http://localhost:8000/v1/command \
  -H "Content-Type: application/json" \
  -d '{
    "command_text": "imagine a sunset",
    "image_base64": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
    "session_id": "test-2"
  }'
```

### Cancel Job
```bash
curl -X POST http://localhost:8000/v1/command \
  -H "Content-Type: application/json" \
  -d '{"command_text": "stop", "session_id": "test-1"}'
```

## API Documentation

Interactive API docs: http://localhost:8000/docs

## Architecture Overview

```
Frontend (Voice) → POST /v1/command → Orchestrator Agent
                                           ↓
                                    ┌──────┴──────┐
                                    │             │
                              Research Agent  Creative Agent
                                    │             │
                              Yutori API    Freepik API
                                    │             │
                                    └──────┬──────┘
                                           ↓
                                    Dialogue Agent
                                           ↓
                                    Speakable Response
```

## Key Features

✅ Multi-agent architecture with clear separation of concerns
✅ Async job execution with status tracking
✅ Cooperative cancellation support
✅ Structured event logging
✅ Session management for conversation context
✅ Speakable response formatting
✅ Robust error handling with safe payloads

## Next Steps

- See README.md for complete documentation
- Run test_api.sh for comprehensive API testing
- Check app/agents/ for agent implementations
- Review app/models.py for data structures
