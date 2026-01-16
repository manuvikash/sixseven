from typing import Dict, Any, Optional
from app.models import Job


class DialogueAgent:
    """Turns internal job results into speakable summaries and structured JSON."""
    
    def format_research_result(self, job: Job) -> Dict[str, Any]:
        """Format research job result into speakable + structured output."""
        if not job.result:
            return {
                "speakable": "Research task is still in progress.",
                "structured": None
            }
        
        result = job.result
        answer = result.get("structured_result", {}).get("answer", "")
        bullets = result.get("structured_result", {}).get("bullets", [])
        
        # Create speakable summary (1-2 sentences + up to 3 bullets)
        speakable_parts = []
        if answer:
            # Take first 2 sentences
            sentences = answer.split(". ")
            speakable_parts.append(". ".join(sentences[:2]) + ("." if len(sentences) > 1 else ""))
        
        if bullets:
            speakable_parts.append("Key points:")
            for bullet in bullets[:3]:
                speakable_parts.append(f"- {bullet}")
        
        speakable = " ".join(speakable_parts) if speakable_parts else "Research completed."
        
        return {
            "speakable": speakable,
            "structured": {
                "answer": answer,
                "bullets": bullets,
                "citations": result.get("structured_result", {}).get("citations", []),
                "view_url": result.get("view_url"),
                "task_id": result.get("task_id")
            }
        }
    
    def format_creative_result(self, job: Job) -> Dict[str, Any]:
        """Format creative job result into speakable + structured output."""
        if not job.result:
            return {
                "speakable": "Creative task is still in progress.",
                "structured": None
            }
        
        result = job.result
        generated_urls = result.get("generated_urls", [])
        
        if generated_urls:
            count = len(generated_urls)
            speakable = f"Generated {count} image{'s' if count > 1 else ''}. Check your screen."
        else:
            speakable = "Creative task completed. Check the response for details."
        
        return {
            "speakable": speakable,
            "structured": {
                "generated_urls": generated_urls,
                "task_id": result.get("task_id"),
                "status": result.get("status")
            }
        }
    
    def format_error(self, job: Job) -> str:
        """Format error into speakable message."""
        if not job.error:
            return "An unknown error occurred."
        
        error_msg = job.error.get("message", "An error occurred")
        return f"Task failed: {error_msg}"
    
    def format_status_message(self, active_job: Optional[Job]) -> str:
        """Format status into speakable message."""
        if not active_job:
            return "No active tasks."
        
        elapsed = (active_job.updated_at - active_job.created_at).total_seconds()
        job_type = active_job.type
        status = active_job.status
        
        if status == "running":
            return f"Your {job_type} task is running. Elapsed time: {int(elapsed)} seconds."
        elif status == "queued":
            return f"Your {job_type} task is queued."
        elif status == "succeeded":
            return f"Your {job_type} task completed successfully."
        elif status == "failed":
            return f"Your {job_type} task failed."
        elif status == "cancelled":
            return f"Your {job_type} task was cancelled."
        
        return f"Your {job_type} task status is {status}."
