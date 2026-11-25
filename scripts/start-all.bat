@echo off
rem One-click start: converter (1453) + backend (8081)
setlocal
set "ROOT=%~dp0"
call "%ROOT%start-converter.bat"

echo.
echo [INFO] starting backend on 8081 ...
cd /d "%ROOT%..\code\aiTools_backend"
call mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
endlocal
