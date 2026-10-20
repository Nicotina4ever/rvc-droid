@echo off
title RVC Tools - Convertitore unico (.pth .pt .index .wav)
echo.
echo ============================================================
echo   RVC TOOLS - Convertitore unico per Android
echo   Trascina qualsiasi file su questo BAT:
echo     .pth   -^> .ptl  OPPURE .onnx (modello vocale; .onnx consigliato)
echo     .index -^> .fbin (index faiss)
echo     .pt    -^> .ptl  (hubert base)
echo     .wav   -^> .mp3 (192k CBR di default)
echo   Oppure eseguilo e scegli il file con l'explorer.
echo.
echo   Per convertire un'intera cartella di modelli legacy in
echo   .onnx + .fbin con un solo click, lancia CONVERTI_BATCH.bat
echo ============================================================
echo.

set PYTHON_EXE=K:\AI\rvc-portable\runtime\python.exe
set FILE=%~1

if not exist "%PYTHON_EXE%" (
    echo [ERRORE] Python non trovato in: %PYTHON_EXE%
    pause
    exit /b 1
)

if "%FILE%"=="" (
    echo [INFO] Nessun file trascinato. Uno script aprira' il selettore file.
    echo.
    set FILE=autodetect
)

if "%FILE%"=="autodetect" (
    echo Apertura selettore file (modello vocale .pth)...
    echo Nota: se vuoi convertire un .index o un .pt,
    echo trascinalo direttamente su questo BAT.
    echo.
    "%PYTHON_EXE%" "%~dp0convert_pth_to_ptl.py"
    pause
    exit /b 0
)

for %%A in ("%FILE%") do set EXT=%%~xA

if /I "%EXT%"==".pth" (
    echo.
    echo Rilevato .pth. Scegli il target:
    echo   [1] .ptl  (TorchScript Mobile, NON piu' usato dall'app)
    echo   [2] .onnx (ONNX Runtime Android, RACCOMANDATO)
    echo.
    set /p CHOICE=Numero [1/2, default 2]:
    if "%CHOICE%"=="1" (
        "%PYTHON_EXE%" "%~dp0convert_pth_to_ptl.py" "%FILE%"
    ) else (
        "%PYTHON_EXE%" "%~dp0convert_pth_to_onnx.py" "%FILE%"
    )
) else if /I "%EXT%"==".index" (
    "%PYTHON_EXE%" "%~dp0export_faiss_bin.py" "%FILE%"
) else if /I "%EXT%"==".pt" (
    "%PYTHON_EXE%" "%~dp0export_hubert_ptl.py" "%FILE%"
) else if /I "%EXT%"==".wav" (
    "%PYTHON_EXE%" "%~dp0export_mp3.py" "%FILE%"
) else (
    echo [ERRORE] Estensione non supportata: %EXT%
    echo          Attesi: .pth ^(voce^), .index ^(faiss^), .pt ^(hubert^), .wav -^> .mp3
    echo.
    echo Suggerimento: per convertire un'intera cartella di modelli legacy in
    echo un colpo solo, usa CONVERTI_BATCH.bat.
    pause
    exit /b 1
) else if /I "%FILE%"=="batch" (
    call "%~dp0CONVERTI_BATCH.bat"
    exit /b %ERRORLEVEL%
)

pause