import httpx
from typing import Optional, Dict, Any
import logging

logger = logging.getLogger(__name__)


async def http_post_with_retry(
    url: str,
    headers: Dict[str, str],
    json_data: Optional[Dict[str, Any]] = None,
    timeout: float = 30.0,
    max_retries: int = 2
) -> Dict[str, Any]:
    """POST with timeout and retries."""
    for attempt in range(max_retries + 1):
        try:
            async with httpx.AsyncClient(timeout=timeout) as client:
                response = await client.post(url, headers=headers, json=json_data)
                response.raise_for_status()
                return response.json()
        except httpx.HTTPStatusError as e:
            logger.error(f"HTTP error on attempt {attempt + 1}: {e.response.status_code} - {e.response.text[:500]}")
            if attempt == max_retries:
                return {
                    "error": True,
                    "status_code": e.response.status_code,
                    "message": e.response.text[:500]
                }
        except Exception as e:
            logger.error(f"Request error on attempt {attempt + 1}: {str(e)}")
            if attempt == max_retries:
                return {
                    "error": True,
                    "message": str(e)
                }
    return {"error": True, "message": "Max retries exceeded"}


async def http_get_with_retry(
    url: str,
    headers: Dict[str, str],
    timeout: float = 30.0,
    max_retries: int = 2
) -> Dict[str, Any]:
    """GET with timeout and retries."""
    for attempt in range(max_retries + 1):
        try:
            async with httpx.AsyncClient(timeout=timeout) as client:
                response = await client.get(url, headers=headers)
                response.raise_for_status()
                return response.json()
        except httpx.HTTPStatusError as e:
            logger.error(f"HTTP error on attempt {attempt + 1}: {e.response.status_code} - {e.response.text[:500]}")
            if attempt == max_retries:
                return {
                    "error": True,
                    "status_code": e.response.status_code,
                    "message": e.response.text[:500]
                }
        except Exception as e:
            logger.error(f"Request error on attempt {attempt + 1}: {str(e)}")
            if attempt == max_retries:
                return {
                    "error": True,
                    "message": str(e)
                }
    return {"error": True, "message": "Max retries exceeded"}
