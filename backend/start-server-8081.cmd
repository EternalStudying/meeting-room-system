@echo off
setlocal

cd /d "%~dp0"

call :LoadEnv SERVER_PUBLIC_IP
call :LoadEnv DB_PORT
call :LoadEnv DB_PASSWORD
call :LoadEnv DB_USERNAME

if not defined BACKEND_PORT set "BACKEND_PORT=8081"

set "RUN_PROFILES="
if exist "%~dp0meeting-room-server\src\main\resources\application-local.yml" (
  set "RUN_PROFILES=-Dspring-boot.run.profiles=local"
)

call mvn -N -DskipTests install
if errorlevel 1 exit /b %errorlevel%

call mvn -pl meeting-room-common clean install -DskipTests "-Dspring-boot.repackage.skip=true"
if errorlevel 1 exit /b %errorlevel%

call mvn -pl meeting-room-server spring-boot:run %RUN_PROFILES% "-Dspring-boot.run.arguments=--server.port=%BACKEND_PORT%"

endlocal
exit /b 0

:LoadEnv
if defined %~1 exit /b 0
for /f "skip=2 tokens=2,*" %%A in ('reg query HKCU\Environment /v %~1 2^>nul') do if not defined %~1 set "%~1=%%B"
if defined %~1 exit /b 0
for /f "skip=2 tokens=2,*" %%A in ('reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v %~1 2^>nul') do if not defined %~1 set "%~1=%%B"
exit /b 0
