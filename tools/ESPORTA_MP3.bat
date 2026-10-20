@echo off
title RVC Tools - Esporta WAV in MP3
echo.
echo ============================================================
echo   RVC Tools - Esporta WAV in MP3
echo   Trascina un file .wav su questo BAT
echo   oppure eseguilo e scegli il file con l'explorer.
echo   Default: 192 kbps CBR, stesso sample rate e canali.
echo ============================================================
echo.

set PYTHON_EXE=K:\AI\rvc-portable\runtime\python.exe
set SCRIPT=%~dp0export_mp3.py

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
