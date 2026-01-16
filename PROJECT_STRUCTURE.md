# Project Structure

```
sixseven/
├── app/
│   ├── __init__.py                 # Package marker
│   ├── main.py                     # FastAPI app + REST endpoints
│   ├── models.py                   # Pydantic models (Job, Session, Request/Response)
│   ├── store.py                    # JobStore interface + InMemoryJobStore
│   ├── orchestrator.py             # Orchestrator Agent (coordinator)
│   │
│   ├── agents/
│   │   ├── __init__.py
│   │   ├── dialogue.py             # Dialogue/Response Agent
│   │   ├── research.py             # Research Agent (Yutori)
│   │   ├── creative.py             # Creative Agent (Freepik)
│   │   ├── status.py               # Status Agent
│   │   └── cancel.py               # Cancellation Agent
│   │
│   └── utils/
│       ├── __init__.py
│       └── http.py                 # HTTP helpers with retry logic
│
├── requirements.txt                # Python dependencies
├── .env.example                    # Environment variables template
├── .gitignore                      # Git ignore rules
│
├── README.md                       # Complete documentation + curl examples
├── QUICKSTART.md                   # 30-second setup guide
├── ARCHITECTURE.md                 # Detailed architecture documentation
├── PROJECT_STRUCTURE.md            # This file
│
├── run.sh                          # Startup script
└── test_api.sh                     # API testing script
```

## File Descriptions

### Core Application Files

**app/main.py** (FastAPI Application)
- REST API endpoints
- CORS configuration
- Request/response handling
- Error handling
- Logging setup

**app/models.py** (Data Models)
- Job: Job state and lifecycle
- Session: Session management
- JobEvent: Event logging
- JobInput: Command input structure
- CommandRequest/Response: API contracts

**app/store.py** (Storage Layer)
- JobStore: Abstract interface
- InMemoryJobStore: In-memory implementation
- Thread-safe operations
- Job and session CRUD

**app/orchestrator.py** (Orchestrator Agent)
- Command routing
- Intent parsing
- Job creation and lifecycle
- Session management
- Agent coordination

### Agent Files

**app/agents/dialogue.py**
- format_research_result()
- format_creative_result()
- format_error()
- format_status_message()

**app/agents/research.py**
- execute() - Main workflow
- _create_task() - Yutori API call
- _poll_task() - Polling loop with cancellation

**app/agents/creative.py**
- execute() - Main workflow
- _generate_image() - Freepik API call
- _extract_urls() - Response parsing

**app/agents/status.py**
- get_status() - Session status query

**app/agents/cancel.py**
- cancel_job() - Cooperative cancellation

### Utility Files

**app/utils/http.py**
- http_post_with_retry()
- http_get_with_retry()
- Timeout and retry logic

### Configuration Files

**.env.example**
```
YUTORI_API_KEY=your_key_here
FREEPIK_API_KEY=your_key_here
```

**requirements.txt**
```
fastapi==0.109.0
uvicorn==0.27.0
pydantic==2.5.3
pydantic-settings==2.1.0
httpx==0.26.0
python-dotenv==1.0.0
```

### Documentation Files

**README.md**
- Complete API documentation
- curl examples for all endpoints
- Architecture overview
- Quick start guide
- Production considerations

**QUICKSTART.md**
- 30-second setup
- Basic test commands
- Architecture diagram
- Key features

**ARCHITECTURE.md**
- Multi-agent design patterns
- Agent responsibilities
- Data flow diagrams
- State management
- Scalability considerations
- Extension points

**PROJECT_STRUCTURE.md**
- This file
- File tree
- File descriptions

### Scripts

**run.sh**
- Virtual environment setup
- Dependency installation
- Server startup

**test_api.sh**
- Comprehensive API testing
- All endpoint examples
- Sample workflows

## Key Design Patterns

### 1. Agent Pattern
Each agent is a separate class with a single responsibility:
- Clear interfaces
- Independent testing
- Easy to extend

### 2. Store Pattern
Abstract storage interface allows swapping implementations:
- InMemoryJobStore for development
- PostgreSQL/Redis for production

### 3. Async Execution
Long-running tasks execute in background:
- Immediate API responses
- Non-blocking operations
- Cooperative cancellation

### 4. Event Sourcing
Job events provide audit trail:
- State transitions
- Progress updates
- Error tracking

### 5. Session Management
Sessions group related commands:
- Conversation context
- Active job tracking
- Multi-turn interactions

## API Endpoints

```
GET  /healthz                    Health check
POST /v1/command                 Main command endpoint
GET  /v1/jobs/{job_id}          Get job details
GET  /v1/jobs                   List jobs (with filters)
POST /v1/jobs/{job_id}/cancel   Cancel job
```

## Environment Variables

```
YUTORI_API_KEY      Required for research tasks
FREEPIK_API_KEY     Required for creative tasks
```

## Dependencies

- **FastAPI**: Modern web framework
- **Uvicorn**: ASGI server
- **Pydantic**: Data validation
- **httpx**: Async HTTP client
- **python-dotenv**: Environment management

## Running the Application

```bash
# Install
pip install -r requirements.txt

# Configure
cp .env.example .env
# Edit .env with your API keys

# Run
uvicorn app.main:app --reload

# Test
./test_api.sh
```

## Development Workflow

1. **Add new agent**: Create file in `app/agents/`
2. **Add new endpoint**: Update `app/main.py`
3. **Add new model**: Update `app/models.py`
4. **Test**: Use `test_api.sh` or curl
5. **Document**: Update README.md

## Production Checklist

- [ ] Replace InMemoryJobStore with persistent storage
- [ ] Add authentication and authorization
- [ ] Implement rate limiting
- [ ] Add monitoring and metrics
- [ ] Configure proper CORS
- [ ] Add request validation
- [ ] Implement job TTL and cleanup
- [ ] Add health checks for dependencies
- [ ] Set up logging aggregation
- [ ] Configure auto-scaling
