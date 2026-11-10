@echo off
title Restaurar backup do StockOS
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0programa\scripts\restaurar.ps1"
echo.
pause
