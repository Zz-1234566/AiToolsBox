@echo off
setlocal
set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
del "%STARTUP%\AIToolsBox-Converter.lnk" 2>nul
echo [OK] autostart removed
pause
endlocal
