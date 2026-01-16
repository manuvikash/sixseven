import os
import time
import asyncio
from typing import Dict, Any, Optional
from app.models import Job
from app.store import JobStore
from app.utils.http import http_post_with_retry, http_get_with_retry
from app.observability import get_logger

logger = get_logger(__name__)


class CreativeAgent:
    """Freepik Creative Agent - generates images using Seedream 4.5 Edit."""
    
    def __init__(self, store: JobStore, observer=None):
        self.store = store
        self.observer = observer
        self.api_key = os.getenv("FREEPIK_API_KEY", "")
        self.base_url = "https://api.freepik.com/v1/ai/text-to-image/seedream-v4-5-edit"
    
    async def execute(self, job: Job, image_base64: Optional[str], 
                     imagination: str = "vivid", aspect_ratio: str = "original"):
        """Execute creative workflow."""
        job.status = "running"
        job.add_event("info", "Creative task started")
        self.store.update_job(job)
        
        logger.info("creative_started", job_id=job.job_id, prompt=job.input.query_or_prompt[:100])
        
        try:
            if not image_base64:
                job.status = "failed"
                job.error = {"message": "No image provided for creative task"}
                job.add_event("error", "No image provided")
                self.store.update_job(job)
                logger.error("creative_no_image", job_id=job.job_id)
                return
            
            # Validate image size (base64 length gives rough estimate)
            # A 1x1 pixel PNG is ~100 chars, 512x512 JPEG is typically 50KB+ (70K+ base64)
            if len(image_base64) < 10000:  # Less than ~7KB
                job.status = "failed"
                job.error = {
                    "message": "Image too small or invalid",
                    "details": f"Image base64 length: {len(image_base64)} chars. Minimum recommended: 10000 chars (~7KB). Please provide a real image (at least 512x512 pixels)."
                }
                job.add_event("error", "Image validation failed: too small")
                self.store.update_job(job)
                logger.error("creative_image_too_small", job_id=job.job_id, base64_length=len(image_base64))
                return
            
            # Call Freepik API
            start_time = time.time()
            result = await self._generate_image(
                image_base64, 
                job.input.query_or_prompt,
                imagination,
                aspect_ratio
            )
            api_duration = time.time() - start_time
            
            if self.observer:
                self.observer.external_api_call("freepik", "generate_image", api_duration, not result.get("error"))
            
            if result.get("error"):
                job.status = "failed"
                job.error = {
                    "message": "Failed to generate image",
                    "details": result.get("message", "Unknown error"),
                    "status_code": result.get("status_code"),
                    "payload": str(result)[:1000]  # Increased from 500 to see more
                }
                job.add_event("error", "Image generation failed", result)
                self.store.update_job(job)
                logger.error("creative_failed", job_id=job.job_id, error=result, status_code=result.get("status_code"))
                return
            
            # Check if response is async (CREATED/IN_PROGRESS)
            status = result.get("status", "").upper()
            
            # Also check data.status
            if "data" in result and "status" in result["data"]:
                status = result["data"]["status"].upper()
            
            task_id = result.get("task_id") or (result.get("data", {}).get("task_id"))
            
            if status in ["CREATED", "IN_PROGRESS", "PENDING"]:
                # Async response - poll for completion
                job.add_event("info", f"Creative task async: {status}", {"status": status, "task_id": task_id})
                logger.info("creative_async", job_id=job.job_id, status=status, task_id=task_id)
                
                # Poll until completion
                poll_result = await self._poll_task(job, task_id)
                
                if job.cancelled:
                    job.status = "cancelled"
                    job.add_event("info", "Creative task cancelled")
                    self.store.update_job(job)
                    logger.info("creative_cancelled", job_id=job.job_id)
                    return
                
                if poll_result.get("error"):
                    job.status = "failed"
                    job.error = {
                        "message": "Creative task failed during polling",
                        "details": poll_result.get("message", "Unknown error"),
                        "freepik_status": poll_result.get("status"),
                        "full_response": str(poll_result.get("full_response", {}))[:1000]
                    }
                    job.add_event("error", "Creative task failed", poll_result)
                    self.store.update_job(job)
                    logger.error("creative_poll_failed", job_id=job.job_id, error=poll_result)
                    return
                
                # Extract URLs from poll result
                generated_urls = self._extract_urls(poll_result)
                
                job.status = "succeeded"
                job.progress = 100
                job.result = {
                    "task_id": task_id,
                    "status": "COMPLETED",
                    "generated_urls": generated_urls,
                    "full_response": poll_result
                }
                job.add_event("info", "Creative task succeeded", {
                    "url_count": len(generated_urls)
                })
                logger.info(
                    "creative_succeeded",
                    job_id=job.job_id,
                    url_count=len(generated_urls),
                    api_duration=api_duration
                )
            else:
                # Synchronous response or already completed - extract URLs
                generated_urls = self._extract_urls(result)
                
                job.status = "succeeded"
                job.progress = 100
                job.result = {
                    "task_id": task_id,
                    "status": status or "COMPLETED",
                    "generated_urls": generated_urls,
                    "full_response": result
                }
                job.add_event("info", "Creative task succeeded", {
                    "url_count": len(generated_urls)
                })
                logger.info(
                    "creative_succeeded",
                    job_id=job.job_id,
                    url_count=len(generated_urls),
                    api_duration=api_duration
                )
            
            self.store.update_job(job)
            
        except Exception as e:
            logger.error("creative_error", job_id=job.job_id, error=str(e), exc_info=True)
            job.status = "failed"
            job.error = {"message": str(e)}
            job.add_event("error", f"Unexpected error: {str(e)}")
            self.store.update_job(job)
    
    async def _generate_image(self, image_base64: str, prompt: str,
                             imagination: str, aspect_ratio: str) -> Dict[str, Any]:
        """Call Freepik Seedream 4.5 Edit API."""
        headers = {
            "x-freepik-api-key": self.api_key,
            "Content-Type": "application/json"
        }
        
        # Map aspect ratio to Seedream format
        aspect_ratio_map = {
            "original": "square_1_1",
            "1:1": "square_1_1",
            "16:9": "widescreen_16_9",
            "9:16": "social_story_9_16",
            "2:3": "portrait_2_3",
            "3:4": "traditional_3_4",
            "3:2": "standard_3_2",
            "4:3": "classic_4_3",
            "21:9": "cinematic_21_9"
        }
        
        seedream_aspect_ratio = aspect_ratio_map.get(aspect_ratio, "square_1_1")
        
        # Clean base64 string - remove data URI prefix if present
        clean_base64 = image_base64
        if image_base64.startswith('data:'):
            # Remove "data:image/jpeg;base64," or similar prefix
            clean_base64 = image_base64.split(',', 1)[1] if ',' in image_base64 else image_base64
        
        logger.info(
            "freepik_request",
            prompt_length=len(prompt),
            image_base64_length=len(clean_base64),
            aspect_ratio=seedream_aspect_ratio,
            has_data_uri_prefix=image_base64.startswith('data:')
        )
        
        payload = {
            "prompt": prompt,
            "reference_images": [clean_base64],
            "aspect_ratio": seedream_aspect_ratio,
            "enable_safety_checker": True
        }
        
        return await http_post_with_retry(self.base_url, headers, payload, timeout=60.0)
    
    async def _poll_task(self, job: Job, task_id: str) -> Dict[str, Any]:
        """Poll Freepik task until completion."""
        headers = {
            "x-freepik-api-key": self.api_key
        }
        
        poll_url = f"{self.base_url}/{task_id}"
        poll_count = 0
        
        while not job.cancelled:
            await asyncio.sleep(3.0)  # Poll every 3 seconds
            poll_count += 1
            
            start_time = time.time()
            result = await http_get_with_retry(poll_url, headers)
            poll_duration = time.time() - start_time
            
            if self.observer:
                self.observer.external_api_call("freepik", "poll_task", poll_duration, not result.get("error"))
            
            if result.get("error"):
                return result
            
            # Check status in response
            status = result.get("status", "").upper()
            if "data" in result and "status" in result["data"]:
                status = result["data"]["status"].upper()
            
            elapsed = poll_count * 3.0
            
            job.add_event("info", f"Polling update: {status}", {
                "status": status,
                "elapsed_seconds": int(elapsed)
            })
            self.store.update_job(job)
            
            if self.observer:
                self.observer.job_progress(job, None, f"Status: {status}, elapsed: {int(elapsed)}s")
            
            # Check for completion
            if status in ["COMPLETED", "SUCCEEDED", "SUCCESS"]:
                logger.info("creative_poll_completed", job_id=job.job_id, poll_count=poll_count)
                return result
            elif status in ["FAILED", "ERROR"]:
                # Get more error details from Freepik response
                error_msg = (
                    result.get("error_message") or 
                    result.get("message") or
                    result.get("data", {}).get("error_message") or
                    result.get("data", {}).get("message") or
                    "Task failed"
                )
                error_details = {
                    "error": True,
                    "message": error_msg,
                    "status": status,
                    "full_response": result
                }
                logger.error(
                    "creative_poll_failed", 
                    job_id=job.job_id, 
                    error_msg=error_msg,
                    freepik_status=status,
                    full_response=str(result)[:500]
                )
                return error_details
            
            # Continue polling for CREATED/IN_PROGRESS/PENDING
        
        return {"error": True, "message": "Task cancelled"}
    
    def _extract_urls(self, result: Dict[str, Any]) -> list:
        """Extract image URLs from Freepik Seedream response."""
        urls = []
        
        # Seedream returns data.generated array
        if "data" in result and "generated" in result["data"]:
            generated = result["data"]["generated"]
            if isinstance(generated, list):
                urls.extend(generated)
        
        # Also check top-level generated field
        if "generated" in result:
            generated = result["generated"]
            if isinstance(generated, list):
                urls.extend(generated)
        
        return urls
