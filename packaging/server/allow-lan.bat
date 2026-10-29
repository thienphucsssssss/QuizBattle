@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Allow-Lan.ps1"
if errorlevel 1 (
    echo Cannot configure LAN access. Run this file again and allow the Administrator request.
) else (
    echo LAN access is ready. You can now run start.bat.
)
pause
