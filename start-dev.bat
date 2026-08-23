@echo off
echo Starting SyncRoom Local Development (Java Spring Boot + Next.js + FastAPI)...

echo Starting Spring Boot Backend (Port 4000)...
cd backend
start cmd /k "mvn spring-boot:run"
cd ..

echo Starting Frontend (Port 3000)...
cd frontend
start cmd /k "npm run dev"
cd ..

echo Starting Python Service (Port 8000)...
cd python-service
if not exist "venv\" (
    python -m venv venv
    call venv\Scripts\activate.bat
    pip install -r requirements.txt
) else (
    call venv\Scripts\activate.bat
)
start cmd /k "uvicorn main:app --host 0.0.0.0 --port 8000 --reload"
cd ..

echo All services started in separate windows!
