@echo off
setlocal

chcp 65001 >nul

set "ROOT=%~dp0"
set "FRONTEND_SCRIPT=%ROOT%frontend\start-frontend-5172.cmd"
set "BACKEND_SCRIPT=%ROOT%backend\start-server-8081.cmd"

if not exist "%FRONTEND_SCRIPT%" (
  echo Missing frontend script: %FRONTEND_SCRIPT%
  exit /b 1
)

if not exist "%BACKEND_SCRIPT%" (
  echo Missing backend script: %BACKEND_SCRIPT%
  exit /b 1
)

echo Starting backend on http://localhost:8081
start "Backend Server - Port 8081" cmd /k call "%BACKEND_SCRIPT%"

echo Starting frontend on http://localhost:5172
start "Frontend Dev Server - Port 5172" cmd /k call "%FRONTEND_SCRIPT%"

echo.
echo Frontend: http://localhost:5172
echo Backend:  http://localhost:8081
echo.
echo Two server windows were opened. Use Ctrl+C in each window to stop them.

endlocal
exit /b 0
