@echo off
cd /d "%~dp0"

echo.
echo ==========================================
echo        Starting SyncRoom
echo ==========================================
echo.

echo Starting Spring Boot Backend...
start "SyncRoom Backend" cmd /k "cd /d ""%~dp0backend"" && mvn spring-boot:run"

echo Starting Frontend...
start "SyncRoom Frontend" cmd /k "cd /d ""%~dp0frontend"" && npm run dev"

echo Starting Python Service...
start "SyncRoom Python" cmd /k "cd /d ""%~dp0python-service"" && venv\Scripts\python.exe -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload"

echo.
echo ==========================================
echo    SyncRoom services are starting...
echo ==========================================
echo.
echo Frontend: http://localhost:3000
echo Backend:  http://localhost:4000
echo Python:   http://localhost:8000
echo.
pause