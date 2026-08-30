@echo off
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\reset-demo.ps1" %*
exit /b %ERRORLEVEL%
