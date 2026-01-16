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

# Load environment variables
load_dotenv()

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Initialize store and orchestrator
store = InMemoryJobStore()
orchestrator = OrchestratorAgent(store)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting sixseven (67) backend")
    yield
    logger.info("Shutting down sixseven (67) backend")


app = FastAPI(
    title="sixseven (67) API",
    description="Agentic backend for voice-controlled research and creative tasks",
    version="1.0.0",
    lifespan=lifespan
)

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
    return {"ok": True}


@app.post("/v1/command", response_model=CommandResponse)
async def handle_command(request: CommandRequest):
    """
    Main command endpoint - receives voice commands and orchestrates workflows.
    """
    logger.info(f"Received command: {request.command_text[:100]}")
    
    try:
        response = await orchestrator.handle_command(request)
        return response
    except Exception as e:
        logger.error(f"Command handling error: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/v1/jobs/{job_id}", response_model=Job)
async def get_job(job_id: str):
    """Get job by ID."""
    job = store.get_job(job_id)
    
    if not job:
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
    jobs = store.list_jobs(
        session_id=session_id,
        type=type,
        status=status,
        limit=limit
    )
    return jobs


@app.post("/v1/jobs/{job_id}/cancel")
async def cancel_job(job_id: str):
    """Cancel a specific job."""
    job = store.get_job(job_id)
    
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    
    if job.status in ["succeeded", "failed", "cancelled"]:
        return {
            "success": False,
            "message": f"Job already {job.status}"
        }
    
    job.cancelled = True
    job.status = "cancelled"
    job.add_event("info", "Job cancelled via API")
    store.update_job(job)
    
    return {
        "success": True,
        "message": "Job cancelled",
        "job_id": job_id
    }
