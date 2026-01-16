#!/bin/bash

# Startup script for sixseven (67) backend

echo "Starting sixseven (67) backend..."
echo ""

# Check if .env exists
if [ ! -f .env ]; then
    echo "Warning: .env file not found. Copying from .env.example..."
    cp .env.example .env
    echo "Please edit .env and add your API keys before running."
    exit 1
fi

# Check if virtual environment exists
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
fi

# Activate virtual environment
source venv/bin/activate

# Install dependencies
echo "Installing dependencies..."
pip install -q -r requirements.txt

echo ""
echo "Starting server at http://localhost:8000"
echo "API docs available at http://localhost:8000/docs"
echo ""

# Run server
uvicorn app.main:app --reload
