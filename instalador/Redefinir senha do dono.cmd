@echo off
title Redefinir senha do dono - StockOS
set /p NUMERO="Numero do dono (deixe vazio se ha so um dono): "
if "%NUMERO%"=="" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0programa\scripts\redefinir-senha-dono.ps1"
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0programa\scripts\redefinir-senha-dono.ps1" -Numero "%NUMERO%"
)
echo.
pause
