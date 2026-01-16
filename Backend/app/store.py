from typing import Dict, List, Optional
from app.models import Job, Session
from datetime import datetime
import threading


class JobStore:
    def create_job(self, job: Job) -> Job:
        raise NotImplementedError

    def get_job(self, job_id: str) -> Optional[Job]:
        raise NotImplementedError

    def update_job(self, job: Job) -> Job:
        raise NotImplementedError

    def list_jobs(self, session_id: Optional[str] = None, 
                  type: Optional[str] = None,
                  status: Optional[str] = None,
                  limit: int = 20) -> List[Job]:
        raise NotImplementedError

    def get_session(self, session_id: str) -> Optional[Session]:
        raise NotImplementedError

    def update_session(self, session: Session) -> Session:
        raise NotImplementedError


class InMemoryJobStore(JobStore):
    def __init__(self):
        self.jobs: Dict[str, Job] = {}
        self.sessions: Dict[str, Session] = {}
        self.lock = threading.Lock()

    def create_job(self, job: Job) -> Job:
        with self.lock:
            self.jobs[job.job_id] = job
            return job

    def get_job(self, job_id: str) -> Optional[Job]:
        with self.lock:
            return self.jobs.get(job_id)

    def update_job(self, job: Job) -> Job:
        with self.lock:
            job.updated_at = datetime.utcnow()
            self.jobs[job.job_id] = job
            return job

    def list_jobs(self, session_id: Optional[str] = None,
                  type: Optional[str] = None,
                  status: Optional[str] = None,
                  limit: int = 20) -> List[Job]:
        with self.lock:
            jobs = list(self.jobs.values())
            
            if session_id:
                jobs = [j for j in jobs if j.session_id == session_id]
            if type:
                jobs = [j for j in jobs if j.type == type]
            if status:
                jobs = [j for j in jobs if j.status == status]
            
            # Sort by created_at descending (newest first)
            jobs.sort(key=lambda j: j.created_at, reverse=True)
            return jobs[:limit]

    def get_session(self, session_id: str) -> Optional[Session]:
        with self.lock:
            return self.sessions.get(session_id)

    def update_session(self, session: Session) -> Session:
        with self.lock:
            session.last_updated_at = datetime.utcnow()
            self.sessions[session.session_id] = session
            return session
