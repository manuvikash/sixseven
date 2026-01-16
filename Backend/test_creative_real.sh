#!/bin/bash

# Test creative agent with a real image

echo "=== Testing Creative Agent with Real Image ==="
echo ""

# Check if image file exists
if [ ! -f "your_image.jpg" ]; then
    echo "Error: your_image.jpg not found"
    echo "Creating a test image..."
    
    # Create a simple colored square using ImageMagick (if available)
    if command -v convert &> /dev/null; then
        convert -size 512x512 xc:blue your_image.jpg
        echo "Created test image: your_image.jpg (512x512 blue square)"
    else
        echo "ImageMagick not found. Please provide your_image.jpg manually."
        exit 1
    fi
fi

# Get image info
echo "Image file info:"
ls -lh your_image.jpg
echo ""

# Convert image to base64
echo "Converting image to base64..."
IMAGE_BASE64=$(base64 -w 0 your_image.jpg)
IMAGE_SIZE=${#IMAGE_BASE64}
echo "Base64 size: $IMAGE_SIZE characters"
echo ""

# Test creative command
echo "Sending creative command to API..."
echo ""

RESPONSE=$(curl -s -X POST http://localhost:8000/v1/command \
  -H "Content-Type: application/json" \
  -d "{
    \"command_text\": \"imagine a beautiful sunset over mountains\",
    \"image_base64\": \"$IMAGE_BASE64\",
    \"session_id\": \"test-creative-$(date +%s)\",
    \"defaults\": {
      \"freepik_imagination\": \"vivid\",
      \"freepik_aspect_ratio\": \"16:9\"
    }
  }")

echo "Response:"
echo "$RESPONSE" | python3 -m json.tool
echo ""

# Extract job_id
JOB_ID=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('job_id', ''))" 2>/dev/null)

if [ -z "$JOB_ID" ]; then
    echo "Error: No job_id in response"
    exit 1
fi

echo "Job ID: $JOB_ID"
echo ""
echo "Monitoring job progress..."
echo ""

# Poll job status
for i in {1..30}; do
    sleep 3
    
    JOB_STATUS=$(curl -s http://localhost:8000/v1/jobs/$JOB_ID)
    
    STATUS=$(echo "$JOB_STATUS" | python3 -c "import sys, json; print(json.load(sys.stdin).get('status', ''))" 2>/dev/null)
    PROGRESS=$(echo "$JOB_STATUS" | python3 -c "import sys, json; print(json.load(sys.stdin).get('progress', 0))" 2>/dev/null)
    
    echo "[$i] Status: $STATUS, Progress: $PROGRESS%"
    
    # Show latest event
    LATEST_EVENT=$(echo "$JOB_STATUS" | python3 -c "import sys, json; events = json.load(sys.stdin).get('events', []); print(events[-1]['message'] if events else 'No events')" 2>/dev/null)
    echo "    Latest: $LATEST_EVENT"
    
    if [ "$STATUS" = "succeeded" ] || [ "$STATUS" = "failed" ] || [ "$STATUS" = "cancelled" ]; then
        echo ""
        echo "=== Final Job Status ==="
        echo "$JOB_STATUS" | python3 -m json.tool
        break
    fi
done

echo ""
echo "=== Test Complete ==="
