#!/bin/bash

# Test script for sixseven (67) API
# Make sure the server is running: uvicorn app.main:app --reload

BASE_URL="http://localhost:8000"

echo "=== Testing sixseven (67) API ==="
echo ""

# 1. Health check
echo "1. Health Check"
curl -s $BASE_URL/healthz | jq .
echo -e "\n"

# 2. Research command
echo "2. Research Command"
RESEARCH_RESPONSE=$(curl -s -X POST $BASE_URL/v1/command \
  -H "Content-Type: application/json" \
  -d '{
    "command_text": "research the latest AI trends in 2026",
    "session_id": "test-session-001"
  }')
echo $RESEARCH_RESPONSE | jq .
JOB_ID=$(echo $RESEARCH_RESPONSE | jq -r .job_id)
echo -e "\n"

# 3. Status command
echo "3. Status Command"
sleep 2
curl -s -X POST $BASE_URL/v1/command \
  -H "Content-Type: application/json" \
  -d '{
    "command_text": "status",
    "session_id": "test-session-001"
  }' | jq .
echo -e "\n"

# 4. Get job details
echo "4. Get Job Details"
if [ ! -z "$JOB_ID" ] && [ "$JOB_ID" != "null" ]; then
  curl -s $BASE_URL/v1/jobs/$JOB_ID | jq .
else
  echo "No job ID available"
fi
echo -e "\n"

# 5. List jobs
echo "5. List Jobs"
curl -s "$BASE_URL/v1/jobs?session_id=test-session-001&limit=5" | jq .
echo -e "\n"

# 6. Creative command (with dummy base64)
echo "6. Creative Command"
CREATIVE_RESPONSE=$(curl -s -X POST $BASE_URL/v1/command \
  -H "Content-Type: application/json" \
  -d '{
    "command_text": "imagine a futuristic city",
    "image_base64": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
    "session_id": "test-session-002"
  }')
echo $CREATIVE_RESPONSE | jq .
CREATIVE_JOB_ID=$(echo $CREATIVE_RESPONSE | jq -r .job_id)
echo -e "\n"

# 7. Cancel job
echo "7. Cancel Job"
if [ ! -z "$CREATIVE_JOB_ID" ] && [ "$CREATIVE_JOB_ID" != "null" ]; then
  curl -s -X POST $BASE_URL/v1/jobs/$CREATIVE_JOB_ID/cancel | jq .
else
  echo "No creative job ID available"
fi
echo -e "\n"

# 8. Stop command
echo "8. Stop Command"
curl -s -X POST $BASE_URL/v1/command \
  -H "Content-Type: application/json" \
  -d '{
    "command_text": "stop",
    "session_id": "test-session-001"
  }' | jq .
echo -e "\n"

echo "=== Test Complete ==="
