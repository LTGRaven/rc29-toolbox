@echo off
title RC29 Toolbox
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Setup-Windows.ps1"
if errorlevel 1 pause
