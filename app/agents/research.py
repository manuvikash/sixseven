import asyncio
import os
from typing import Dict, Any
from app.models import Job
from app.store import JobStore
from app.utils.http import http_post_with_retry, http_get_with_retry
import logging

logger = logging.getLogger(__name__)


class ResearchAgent:
    """Yutori Research Agent - creates and polls research tasks."""
    
    def __init__(self, store: JobStore):
        self.store = store
        self.api_key = os.getenv("YUTORI_API_KEY", "")
        self.base_url = "https://api.yutori.com/v1/research/tasks"
    
    async def execute(self, job: Job, timezone: str = "America/Los_Angeles"):
        """Execute research workflow."""
        job.status = "running"
        job.add_event("info", "Research task started")
        self.store.update_job(job)
        
        try:
            # Create Yutori task
            task_data = await self._create_task(job.input.query_or_prompt, timezone)
            
            if task_data.get("error"):
                job.status = "failed"
                job.error = {
                    "message": "Failed to create research task",
                    "details": task_data.get("message", "Unknown error")
                }
                job.add_event("error", "Failed to create research task", task_data)
                self.store.update_job(job)
                return
            
            task_id = task_data.get("id") or task_data.get("task_id")
            job.add_event("info", f"Research task created: {task_id}", {"task_id": task_id})
            self.store.update_job(job)
            
            # Poll until completion
            result = await self._poll_task(job, task_id)
            
            if job.cancelled:
                job.status = "cancelled"
                job.add_event("info", "Research task cancelled")
                self.store.update_job(job)
                return
            
            if result.get("error"):
                job.status = "failed"
                job.error = {
                    "message": "Research task failed",
                    "details": result.get("message", "Unknown error")
                }
                job.add_event("error", "Research task failed", result)
                self.store.update_job(job)
                return
            
            # Extract structured result
            structured_result = result.get("output", {})
            
            job.status = "succeeded"
            job.progress = 100
            job.result = {
                "task_id": task_id,
                "view_url": result.get("view_url"),
                "structured_result": structured_result,
                "markdown_result": result.get("markdown")
            }
            job.add_event("info", "Research task succeeded")
            self.store.update_job(job)
            
        except Exception as e:
            logger.error(f"Research agent error: {str(e)}")
            job.status = "failed"
            job.error = {"message": str(e)}
            job.add_event("error", f"Unexpected error: {str(e)}")
            self.store.update_job(job)
    
    async def _create_task(self, query: str, timezone: str) -> Dict[str, Any]:
        """Create Yutori research task."""
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }
        
        payload = {
            "query": query,
            "user_timezone": timezone,
            "task_spec": {
                "output_schema": {
                    "type": "object",
                    "properties": {
                        "answer": {"type": "string"},
                        "bullets": {
                            "type": "array",
                            "items": {"type": "string"}
                        },
                        "citations": {
                            "type": "array",
                            "items": {"type": "string"}
                        }
                    },
                    "required": ["answer", "bullets", "citations"]
                }
            }
        }
        
        return await http_post_with_retry(self.base_url, headers, payload)
    
    async def _poll_task(self, job: Job, task_id: str) -> Dict[str, Any]:
        """Poll Yutori task until completion."""
        headers = {
            "Authorization": f"Bearer {self.api_key}"
        }
        
        poll_url = f"{self.base_url}/{task_id}"
        poll_count = 0
        
        while not job.cancelled:
            await asyncio.sleep(2.5)
            poll_count += 1
            
            result = await http_get_with_retry(poll_url, headers)
            
            if result.get("error"):
                return result
            
            status = result.get("status", "").lower()
            elapsed = poll_count * 2.5
            
            job.add_event("info", f"Polling update: {status}", {
                "status": status,
                "elapsed_seconds": int(elapsed)
            })
            self.store.update_job(job)
            
            if status in ["succeeded", "completed", "success"]:
                return result
            elif status in ["failed", "error"]:
                return {"error": True, "message": result.get("error_message", "Task failed")}
            
            # Continue polling for queued/running/in_progress
        
        return {"error": True, "message": "Task cancelled"}
