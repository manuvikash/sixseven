from typing import Optional, Dict, Any
from app.models import Job, Session
from app.store import JobStore
from app.agents.dialogue import DialogueAgent


class StatusAgent:
    """Reports current system state for a session."""
    
    def __init__(self, store: JobStore, dialogue_agent: DialogueAgent):
        self.store = store
        self.dialogue_agent = dialogue_agent
    
    def get_status(self, session_id: str) -> Dict[str, Any]:
        """Get status for a session."""
        session = self.store.get_session(session_id)
        
        if not session or not session.active_job_id:
            return {
                "active_job": None,
                "message": self.dialogue_agent.format_status_message(None)
            }
        
        job = self.store.get_job(session.active_job_id)
        
        if not job:
            return {
                "active_job": None,
                "message": "No active tasks."
            }
        
        elapsed = (job.updated_at - job.created_at).total_seconds()
        last_event = job.events[-1] if job.events else None
        
        active_job_summary = {
            "job_id": job.job_id,
            "type": job.type,
            "status": job.status,
            "elapsed_seconds": int(elapsed),
            "last_event": {
                "message": last_event.message,
                "ts": last_event.ts.isoformat()
            } if last_event else None
        }
        
        message = self.dialogue_agent.format_status_message(job)
        
        return {
            "active_job": active_job_summary,
            "message": message
        }
