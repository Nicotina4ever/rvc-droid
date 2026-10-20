@echo off
title RVC Hubert - Export .pt → .ptl per Android
echo.
echo ============================================================
echo   RVC Hubert Base .pt → TorchScript Mobile (.ptl) Exporter
echo ============================================================
echo.

set PYTHON_EXE=K:\AI\rvc-portable\runtime\python.exe
set SCRIPT=%~dp0export_hubert_ptl.py

if not exist "%PYTHON_EXE%" (
    echo [ERRORE] Python non trovato in: %PYTHON_EXE%
    pause
    exit /b 1
)

if "%~1"=="" (
    echo Uso: Trascina un file .pt su questo BAT
    echo      oppure verra' usato hubert_base.pt predefinito.
    echo.
    "%PYTHON_EXE%" "%SCRIPT%"
) else (
    "%PYTHON_EXE%" "%SCRIPT%" "%~1"
)

pause
