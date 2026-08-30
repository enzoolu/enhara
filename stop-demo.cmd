@echo off
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop-demo.ps1" %*
exit /b %ERRORLEVEL%
