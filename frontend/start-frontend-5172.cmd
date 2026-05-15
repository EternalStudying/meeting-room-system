@echo off
setlocal

chcp 65001 >nul
cd /d "%~dp0"
title Frontend Dev Server - Port 5172

echo Starting frontend dev server on http://localhost:5172
echo Press Ctrl+C to stop the server.
echo.

if "%~1"=="" (
  call npm run dev:5172
) else (
  call npm run dev:5172 -- %*
)
set "EXIT_CODE=%ERRORLEVEL%"

echo.
echo Frontend dev server stopped or failed to start.
if "%~1"=="" pause

endlocal
exit /b %EXIT_CODE%
