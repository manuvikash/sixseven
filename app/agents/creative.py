import os
from typing import Dict, Any, Optional
from app.models import Job
from app.store import JobStore
from app.utils.http import http_post_with_retry
import logging

logger = logging.getLogger(__name__)


class CreativeAgent:
    """Freepik Creative Agent - generates images using Reimagine Flux."""
    
    def __init__(self, store: JobStore):
        self.store = store
        self.api_key = os.getenv("FREEPIK_API_KEY", "")
        self.base_url = "https://api.freepik.com/v1/ai/beta/text-to-image/reimagine-flux"
    
    async def execute(self, job: Job, image_base64: Optional[str], 
                     imagination: str = "vivid", aspect_ratio: str = "original"):
        """Execute creative workflow."""
        job.status = "running"
        job.add_event("info", "Creative task started")
        self.store.update_job(job)
        
        try:
            if not image_base64:
                job.status = "failed"
                job.error = {"message": "No image provided for creative task"}
                job.add_event("error", "No image provided")
                self.store.update_job(job)
                return
            
            # Call Freepik API
            result = await self._generate_image(
                image_base64, 
                job.input.query_or_prompt,
                imagination,
                aspect_ratio
            )
            
            if result.get("error"):
                job.status = "failed"
                job.error = {
                    "message": "Failed to generate image",
                    "details": result.get("message", "Unknown error"),
                    "payload": str(result)[:500]
                }
                job.add_event("error", "Image generation failed", result)
                self.store.update_job(job)
                return
            
            # Check if response is async (CREATED/IN_PROGRESS)
            status = result.get("status", "").upper()
            
            if status in ["CREATED", "IN_PROGRESS", "PENDING"]:
                # Async response - store payload and mark as succeeded with note
                job.status = "succeeded"
                job.result = {
                    "task_id": result.get("id") or result.get("task_id"),
                    "status": status,
                    "generated_urls": [],
                    "async_note": "Task is pending/in-progress. Polling not implemented.",
                    "full_response": result
                }
                job.add_event("info", f"Creative task async: {status}", {"status": status})
            else:
                # Synchronous response - extract URLs
                generated_urls = self._extract_urls(result)
                
                job.status = "succeeded"
                job.progress = 100
                job.result = {
                    "task_id": result.get("id") or result.get("task_id"),
                    "status": status or "COMPLETED",
                    "generated_urls": generated_urls,
                    "full_response": result
                }
                job.add_event("info", "Creative task succeeded", {
                    "url_count": len(generated_urls)
                })
            
            self.store.update_job(job)
            
        except Exception as e:
            logger.error(f"Creative agent error: {str(e)}")
            job.status = "failed"
            job.error = {"message": str(e)}
            job.add_event("error", f"Unexpected error: {str(e)}")
            self.store.update_job(job)
    
    async def _generate_image(self, image_base64: str, prompt: str,
                             imagination: str, aspect_ratio: str) -> Dict[str, Any]:
        """Call Freepik Reimagine Flux API."""
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }
        
        payload = {
            "image": image_base64,
            "prompt": prompt,
            "imagination": imagination,
            "aspect_ratio": aspect_ratio
        }
        
        return await http_post_with_retry(self.base_url, headers, payload, timeout=60.0)
    
    def _extract_urls(self, result: Dict[str, Any]) -> list:
        """Extract image URLs from Freepik response."""
        urls = []
        
        # Try common response structures
        if "data" in result:
            data = result["data"]
            if isinstance(data, list):
                for item in data:
                    if "url" in item:
                        urls.append(item["url"])
            elif isinstance(data, dict) and "url" in data:
                urls.append(data["url"])
        
        if "url" in result:
            urls.append(result["url"])
        
        if "images" in result:
            for img in result["images"]:
                if "url" in img:
                    urls.append(img["url"])
        
        return urls
