@echo off
title Instalar StockOS
if not exist "%~dp0programa\scripts\instalar.ps1" (
  echo.
  echo Extraia o arquivo ZIP antes de instalar:
  echo clique com o botao direito no ZIP, Extrair tudo, e abra este arquivo na pasta extraida.
  echo.
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0programa\scripts\instalar.ps1" %*
echo.
pause
