from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from typing import Optional, List
import logging
import os
from dotenv import load_dotenv

from app.models import CommandRequest, CommandResponse, Job, JobListQuery
from app.store import InMemoryJobStore
from app.orchestrator import OrchestratorAgent
from app.observability import (
    setup_structlog,
    setup_tracing,
    setup_metrics,
    get_logger,
    ObservabilityMiddleware,
    job_observer,
    get_metrics_handler,
    request_id_var,
    session_id_var
)

# Load environment variables
load_dotenv()

# Setup observability
setup_structlog()
tracer = setup_tracing("sixseven-backend")
meter = setup_metrics("sixseven-backend")

# Get structured logger
logger = get_logger(__name__)

# Initialize store and orchestrator
store = InMemoryJobStore()
orchestrator = OrchestratorAgent(store, job_observer)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("starting_backend", service="sixseven", version="1.0.0")
    yield
    logger.info("shutting_down_backend")


app = FastAPI(
    title="sixseven (67) API",
    description="Agentic backend for voice-controlled research and creative tasks",
    version="1.0.0",
    lifespan=lifespan
)

# Add observability middleware
app.add_middleware(ObservabilityMiddleware)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/healthz")
async def health_check():
    """Health check endpoint."""
    logger.info("health_check")
    return {"ok": True}


@app.get("/metrics")
async def metrics():
    """Prometheus metrics endpoint."""
    return await get_metrics_handler()(None)


@app.post("/v1/command", response_model=CommandResponse)
async def handle_command(request: CommandRequest):
    """
    Main command endpoint - receives voice commands and orchestrates workflows.
    """
    # Set session context
    if request.session_id:
        session_id_var.set(request.session_id)
    
    logger.info(
        "command_received",
        command=request.command_text[:100],
        has_image=request.image_base64 is not None,
        session_id=request.session_id
    )
    
    try:
        with tracer.start_as_current_span("handle_command") as span:
            span.set_attribute("command.text", request.command_text[:100])
            span.set_attribute("command.has_image", str(request.image_base64 is not None))
            
            response = await orchestrator.handle_command(request)
            
            span.set_attribute("response.intent", response.intent)
            span.set_attribute("response.job_id", response.job_id or "none")
            
            logger.info(
                "command_handled",
                intent=response.intent,
                job_id=response.job_id,
                session_id=response.session_id
            )
            
            return response
    except Exception as e:
        logger.error(
            "command_error",
            error=str(e),
            error_type=type(e).__name__,
            exc_info=True
        )
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/v1/jobs/{job_id}", response_model=Job)
async def get_job(job_id: str):
    """Get job by ID."""
    logger.info("get_job", job_id=job_id)
    
    job = store.get_job(job_id)
    
    if not job:
        logger.warning("job_not_found", job_id=job_id)
        raise HTTPException(status_code=404, detail="Job not found")
    
    return job


@app.get("/v1/jobs", response_model=List[Job])
async def list_jobs(
    session_id: Optional[str] = Query(None),
    type: Optional[str] = Query(None),
    status: Optional[str] = Query(None),
    limit: int = Query(20, ge=1, le=100)
):
    """List jobs with optional filters."""
    logger.info(
        "list_jobs",
        session_id=session_id,
        type=type,
        status=status,
        limit=limit
    )
    
    jobs = store.list_jobs(
        session_id=session_id,
        type=type,
        status=status,
        limit=limit
    )
    
    logger.info("jobs_listed", count=len(jobs))
    return jobs


@app.post("/v1/jobs/{job_id}/cancel")
async def cancel_job(job_id: str):
    """Cancel a specific job."""
    logger.info("cancel_job_request", job_id=job_id)
    
    job = store.get_job(job_id)
    
    if not job:
        logger.warning("cancel_job_not_found", job_id=job_id)
        raise HTTPException(status_code=404, detail="Job not found")
    
    if job.status in ["succeeded", "failed", "cancelled"]:
        logger.info("cancel_job_already_terminal", job_id=job_id, status=job.status)
        return {
            "success": False,
            "message": f"Job already {job.status}"
        }
    
    job.cancelled = True
    job.status = "cancelled"
    job.add_event("info", "Job cancelled via API")
    store.update_job(job)
    
    job_observer.job_completed(job)
    
    logger.info("job_cancelled", job_id=job_id)
    
    return {
        "success": True,
        "message": "Job cancelled",
        "job_id": job_id
    }


@app.get("/v1/debug/test-freepik")
async def test_freepik():
    """Test Freepik API key."""
    import httpx
    import os
    
    api_key = os.getenv("FREEPIK_API_KEY", "")
    
    if not api_key:
        return {"error": "FREEPIK_API_KEY not set in environment"}
    
    # Test with minimal request
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                "https://api.freepik.com/v1/ai/text-to-image/seedream-v4-5-edit",
                headers={
                    "x-freepik-api-key": api_key,
                    "Content-Type": "application/json"
                },
                json={
                    "prompt": "test image",
                    "reference_images": ["iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="],
                    "aspect_ratio": "square_1_1"
                }
            )
            
            result = {
                "status_code": response.status_code,
                "api_key_prefix": api_key[:15] + "...",
                "api_key_length": len(api_key),
                "headers_sent": {
                    "x-freepik-api-key": api_key[:15] + "...",
                    "Content-Type": "application/json"
                }
            }
            
            if response.status_code == 200:
                result["response"] = response.json()
            else:
                result["error_text"] = response.text[:1000]
                result["response_headers"] = dict(response.headers)
            
            return result
    except Exception as e:
        return {
            "error": str(e),
            "error_type": type(e).__name__,
            "api_key_prefix": api_key[:15] + "...",
            "api_key_length": len(api_key)
        }
