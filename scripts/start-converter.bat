@echo off
rem Start Universal Converter service (FastAPI :1453)
rem Adjust UCF_HOME / UCF_PY if the project is deployed elsewhere.
setlocal
set "UCF_HOME=%USERPROFILE%\.minimax-agent-cn\projects\universal-file-converter"
set "UCF_PY=%USERPROFILE%\.minimax-agent-cn\projects\ufc-venv\Scripts\python.exe"

if not exist "%UCF_PY%" (
  echo [ERROR] python not found: %UCF_PY%
  exit /b 1
)
if not exist "%UCF_HOME%\app\main.py" (
  echo [ERROR] converter not found: %UCF_HOME%
  exit /b 1
)

netstat -ano | findstr ":1453" | findstr "LISTENING" >nul && (
  echo [INFO] converter already listening on 1453
  exit /b 0
)

cd /d "%UCF_HOME%"
start "converter" /min "%UCF_PY%" -m uvicorn app.main:app --host 127.0.0.1 --port 1453
echo [OK] converter started: http://127.0.0.1:1453
endlocal
