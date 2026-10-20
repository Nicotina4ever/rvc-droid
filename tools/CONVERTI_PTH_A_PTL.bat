@echo off
title RVC v2 - Conversione .pth → .ptl per Android
echo.
echo ============================================================
echo   RVC v2 .pth → TorchScript Mobile (.ptl) Converter
echo ============================================================
echo.

set PYTHON_EXE=K:\AI\rvc-portable\runtime\python.exe
set SCRIPT=%~dp0convert_pth_to_ptl.py

if not exist "%PYTHON_EXE%" (
    echo [ERRORE] Python non trovato in: %PYTHON_EXE%
    pause
    exit /b 1
)

if "%~1"=="" (
    echo Uso: Trascina un file .pth su questo BAT
    echo      oppure verra' aperto un selettore file.
    echo.
    "%PYTHON_EXE%" "%SCRIPT%"
) else (
    "%PYTHON_EXE%" "%SCRIPT%" "%~1"
)

pause
