@echo off
title RVC .pth -> .onnx per Android
echo.
echo ============================================================
echo   RVC .pth -^> .onnx per Android (ONNX Runtime 1.17.1)
echo   Trascina un file .pth su questo BAT
echo   oppure eseguilo e scegli il file con l'explorer.
echo   Output: stessa cartella del .pth (es. Nicola-Spot.onnx).
echo ============================================================
echo.

set PYTHON_EXE=K:\AI\rvc-portable\runtime\python.exe
set SCRIPT=%~dp0convert_pth_to_onnx.py

if not exist "%PYTHON_EXE%" (
    echo [ERRORE] Python non trovato in: %PYTHON_EXE%
    pause
    exit /b 1
)

if "%~1"=="" (
    "%PYTHON_EXE%" "%SCRIPT%"
) else (
    "%PYTHON_EXE%" "%SCRIPT%" "%~1"
)

echo.
pause
