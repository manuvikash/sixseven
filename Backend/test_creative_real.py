#!/usr/bin/env python3
"""Test creative agent with a real image."""

import asyncio
import base64
import json
import time
import sys
from pathlib import Path
import httpx


def create_test_image():
    """Create a simple test image if none exists."""
    try:
        from PIL import Image
        
        # Create a 512x512 gradient image
        img = Image.new('RGB', (512, 512))
        pixels = img.load()
        
        for i in range(512):
            for j in range(512):
                # Create a gradient from blue to purple
                r = int(i / 512 * 128)
                g = int(j / 512 * 64)
                b = 255 - int(i / 512 * 100)
                pixels[i, j] = (r, g, b)
        
        img.save('test_image.jpg', 'JPEG', quality=85)
        print("✓ Created test_image.jpg (512x512 gradient)")
        return 'test_image.jpg'
    except ImportError:
        print("✗ PIL not available, cannot create test image")
        return None


def load_image_base64(image_path):
    """Load image and convert to base64."""
    try:
        with open(image_path, 'rb') as f:
            image_data = f.read()
        
        base64_str = base64.b64encode(image_data).decode('utf-8')
        
        print(f"✓ Loaded {image_path}")
        print(f"  Size: {len(image_data)} bytes")
        print(f"  Base64 length: {len(base64_str)} characters")
        
        return base64_str
    except Exception as e:
        print(f"✗ Error loading image: {e}")
        return None


async def test_creative_command(image_base64, prompt="a beautiful sunset over mountains"):
    """Test creative command with real image."""
    
    print(f"\n{'='*60}")
    print("Testing Creative Agent with Real Image")
    print(f"{'='*60}\n")
    
    # Send command
    print(f"Prompt: {prompt}")
    print("Sending command to API...")
    
    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.post(
            "http://localhost:8000/v1/command",
            json={
                "command_text": f"imagine {prompt}",
                "image_base64": image_base64,
                "session_id": f"test-creative-{int(time.time())}",
                "defaults": {
                    "freepik_imagination": "vivid",
                    "freepik_aspect_ratio": "16:9"
                }
            }
        )
        
        if response.status_code != 200:
            print(f"✗ API error: {response.status_code}")
            print(response.text)
            return None
        
        result = response.json()
        print(f"✓ Command accepted")
        print(f"  Intent: {result.get('intent')}")
        print(f"  Message: {result.get('message')}")
        print(f"  Job ID: {result.get('job_id')}")
        
        job_id = result.get('job_id')
        if not job_id:
            print("✗ No job_id in response")
            return None
        
        # Poll job status
        print(f"\nMonitoring job progress...")
        print(f"{'-'*60}")
        
        for i in range(40):  # Poll for up to 2 minutes
            await asyncio.sleep(3)
            
            job_response = await client.get(f"http://localhost:8000/v1/jobs/{job_id}")
            
            if job_response.status_code != 200:
                print(f"✗ Error fetching job: {job_response.status_code}")
                break
            
            job = job_response.json()
            status = job.get('status') or 'unknown'
            progress = job.get('progress') or 0
            
            # Get latest event
            events = job.get('events', [])
            latest_event = events[-1]['message'] if events else 'No events'
            
            print(f"[{i+1:2d}] Status: {status:12s} Progress: {progress:3d}%  |  {latest_event}")
            
            if status in ['succeeded', 'failed', 'cancelled']:
                print(f"\n{'='*60}")
                print(f"Final Status: {status.upper()}")
                print(f"{'='*60}\n")
                
                if status == 'succeeded':
                    result = job.get('result', {})
                    urls = result.get('generated_urls', [])
                    print(f"✓ Generated {len(urls)} image(s):")
                    for idx, url in enumerate(urls, 1):
                        print(f"  {idx}. {url}")
                    
                    print(f"\nFull result:")
                    print(json.dumps(result, indent=2))
                
                elif status == 'failed':
                    error = job.get('error', {})
                    print(f"✗ Error: {error.get('message')}")
                    print(f"  Details: {error.get('details')}")
                    
                    if 'full_response' in error:
                        print(f"\nFreepik response:")
                        print(error['full_response'])
                
                print(f"\n{'='*60}")
                print("All Events:")
                print(f"{'='*60}")
                for event in events:
                    ts = event.get('timestamp', 'N/A')
                    level = event.get('level', 'INFO')
                    msg = event.get('message', '')
                    print(f"[{ts}] {level:5s} {msg}")
                
                return job
        
        print("\n✗ Timeout waiting for job completion")
        return None


async def main():
    """Main test function."""
    
    # Check for existing image
    image_path = None
    
    if Path('your_image.jpg').exists():
        image_path = 'your_image.jpg'
        print("Found your_image.jpg")
    elif Path('test_image.jpg').exists():
        image_path = 'test_image.jpg'
        print("Found test_image.jpg")
    else:
        print("No image found, creating test image...")
        image_path = create_test_image()
    
    if not image_path:
        print("\n✗ No image available for testing")
        print("Please provide your_image.jpg or install PIL to create a test image")
        sys.exit(1)
    
    # Load image
    image_base64 = load_image_base64(image_path)
    if not image_base64:
        sys.exit(1)
    
    # Test creative command
    await test_creative_command(image_base64)


if __name__ == '__main__':
    import asyncio
    asyncio.run(main())
