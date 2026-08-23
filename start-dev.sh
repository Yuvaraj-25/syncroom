#!/bin/bash

# SyncRoom Local Development Startup Script

echo "Starting SyncRoom Development Services..."

# 1. Start Spring Boot Backend
echo "Starting Spring Boot Backend on port 4000..."
cd backend
mvn spring-boot:run &
BACKEND_PID=$!
cd ..

# 2. Start Frontend
echo "Starting Next.js Frontend on port 3000..."
cd frontend
npm run dev &
FRONTEND_PID=$!
cd ..

# 3. Start Python Service
echo "Starting Python Service on port 8000..."
cd python-service
if [ ! -d "venv" ]; then
    python3 -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
else
    source venv/bin/activate
fi
uvicorn main:app --host 0.0.0.0 --port 8000 --reload &
PYTHON_PID=$!
cd ..

echo "All services running!"
echo "Backend:  http://localhost:4000"
echo "Frontend: http://localhost:3000"
echo "Python:   http://localhost:8000"

# Trap Ctrl+C to kill all background processes
trap "kill $BACKEND_PID $FRONTEND_PID $PYTHON_PID 2>/dev/null; exit" SIGINT SIGTERM
wait
