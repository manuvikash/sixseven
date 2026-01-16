from typing import Optional
from app.models import Job
from app.store import JobStore
import logging

logger = logging.getLogger(__name__)


class CancellationAgent:
    """Cancels active jobs for a session."""
    
    def __init__(self, store: JobStore):
        self.store = store
    
    def cancel_job(self, session_id: Optional[str] = None) -> Optional[str]:
        """Cancel active job for session (or globally if session_id is None)."""
        
        if session_id:
            session = self.store.get_session(session_id)
            if not session or not session.active_job_id:
                return None
            
            job = self.store.get_job(session.active_job_id)
        else:
            # Global cancel - find any running job
            running_jobs = self.store.list_jobs(status="running", limit=1)
            if not running_jobs:
                queued_jobs = self.store.list_jobs(status="queued", limit=1)
                job = queued_jobs[0] if queued_jobs else None
            else:
                job = running_jobs[0]
        
        if not job:
            return None
        
        if job.status in ["succeeded", "failed", "cancelled"]:
            return None
        
        # Set cancellation flag
        job.cancelled = True
        job.status = "cancelled"
        job.add_event("info", "Job cancelled by user")
        self.store.update_job(job)
        
        logger.info(f"Job {job.job_id} cancelled")
        
        return job.job_id
