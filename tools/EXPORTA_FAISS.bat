@echo off
title RVC Faiss Index - Esporta .index  .fbin per Android
echo.
echo ============================================================
echo   RVC Faiss Index - .index  .fbin Exporter
echo ============================================================
echo.

set PYTHON_EXE=K:\AI\rvc-portable\runtime\python.exe
set SCRIPT=%~dp0export_faiss_bin.py

if not exist "%PYTHON_EXE%" (
    echo [ERRORE] Python non trovato in: %PYTHON_EXE%
    pause
    exit /b 1
)

if "%~1"=="" (
    echo Uso: Trascina un file .index su questo BAT
    echo      oppure verra' aperto un selettore file.
    echo.
    "%PYTHON_EXE%" "%SCRIPT%"
) else (
    "%PYTHON_EXE%" "%SCRIPT%" "%~1"
)

pause