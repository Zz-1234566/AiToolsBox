@echo off
rem Register converter startup for current user (Startup folder shortcut)
setlocal
set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "TARGET=%~dp0start-converter.bat"
powershell -NoProfile -Command "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('%STARTUP%\AIToolsBox-Converter.lnk'); $s.TargetPath='%TARGET%'; $s.WorkingDirectory='%~dp0'; $s.WindowStyle=7; $s.Save()"
if exist "%STARTUP%\AIToolsBox-Converter.lnk" (
  echo [OK] autostart installed: %STARTUP%\AIToolsBox-Converter.lnk
) else (
  echo [ERROR] failed, put a shortcut of start-converter.bat into: %STARTUP%
)
pause
endlocal
