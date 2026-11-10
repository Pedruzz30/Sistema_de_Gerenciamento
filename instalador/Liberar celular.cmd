@echo off
title Liberar celular do atendente - StockOS
echo Libera o celular do atendente pelo Wi-Fi da loja.
echo O Windows vai pedir permissao de administrador.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0programa\scripts\liberar-celular.ps1"
