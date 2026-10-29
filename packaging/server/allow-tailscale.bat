@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Allow-Tailscale.ps1"
if errorlevel 1 (echo Could not configure Tailscale access.) else (echo Tailscale access rule is ready. Start the Server and test from a Client.)
pause
