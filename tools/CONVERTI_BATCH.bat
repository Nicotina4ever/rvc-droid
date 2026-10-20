@echo off
setlocal EnableDelayedExpansion
title RVC Tools - Conversione batch (.pth + .index -> .onnx + .fbin)
chcp 65001 >nul

echo.
echo ============================================================
echo   RVC TOOLS - Conversione batch per Android
echo   Converte tutti i .pth + .index di una cartella in
echo   .onnx + .fbin con un solo click.
echo.
echo   USO 1: doppio click, poi scegli cartelle nei dialog
echo   USO 2: CONVERTI_BATCH.bat ^<input^> ^<output^>    (no dialog)
echo   USO 3: CONVERTI_BATCH.bat -folder ^<in^> ^<out^>  (no dialog)
echo   USO 4: CONVERTI_BATCH.bat ^<in^> ^<out^> index    (solo .index -^> .fbin)
echo   USO 5: CONVERTI_BATCH.bat ^<in^> ^<out^> voices   (solo .pth/.pt)
echo.
echo   Trascinando uno o piu' file .pth / .index / .pt
echo   direttamente sul BAT, viene chiesta solo la cartella
echo   di output e ogni file viene convertito singolarmente
echo   (l'abbinamento .pth-^>.index viene fatto per nome).
echo ============================================================
echo.

set PYTHON_EXE=K:\AI\rvc-portable\runtime\python.exe
set SCRIPT=%~dp0converti_batch.py

if not exist "%PYTHON_EXE%" (
    echo [ERRORE] Python non trovato in: %PYTHON_EXE%
    pause
    exit /b 1
)

REM -----------------------------------------------------------
REM Modalita' CLI: -folder ^<in^> ^<out^>  oppure  ^<in^> ^<out^> [index|voices|hubert]
REM Questa sezione precede il drag-and-drop per evitare
REM che due cartelle passate in CLI vengano interpretate come
REM drag-and-drop.
REM -----------------------------------------------------------
if /I "%~1"=="-folder" (
    set IN_DIR=%~2
    set OUT_DIR=%~3
    set CHOICE=%~4
    goto DO_CHOICE
)

if not "%~1"=="" if not "%~2"=="" (
    set IN_DIR=%~1
    set OUT_DIR=%~2
    set CHOICE=%~3
    goto DO_CHOICE
)

REM -----------------------------------------------------------
REM Modalita' "drag-and-drop": un singolo file o una cartella
REM (N.B.: con 2+ argomenti siamo gia' in CLI mode, qui sopra.)
REM -----------------------------------------------------------
set "DRAG=%~1"
if not "%DRAG%"=="" (
    if exist "%DRAG%\*" (
        REM Cartella trascinata
        set IN_DIR=%DRAG%
        goto ASK_OUT
    ) else if not "%DRAG:~1,1%"=="-" (
        REM File singolo
        for %%F in ("%DRAG%") do set "DRAG_DIR=%%~dpF"
        set IN_DIR=%DRAG_DIR%
        goto ASK_OUT
    )
)

REM -----------------------------------------------------------
REM Modalita' "dialog Windows Forms" (doppio click sul BAT)
REM -----------------------------------------------------------
echo Apertura dialog per la cartella di INPUT...
for /f "delims=" %%I in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "Add-Type -AssemblyName System.Windows.Forms | Out-Null; $f = New-Object System.Windows.Forms.FolderBrowserDialog; $f.Description = 'Seleziona la cartella con i .pth e .index (o trascinali qui sotto)'; $f.ShowNewFolderButton = $false; if ($f.ShowDialog() -eq 'OK') { Write-Output $f.SelectedPath }"') do set IN_DIR=%%I

if "%IN_DIR%"=="" (
    echo.
    echo [INFO] Dialog chiuso senza selezione. Trascina uno o piu'
    echo        file .pth/.index/.pt direttamente su questo BAT,
    echo        oppure riavvialo per riprovare.
    echo.
    pause
    exit /b 0
)

:ASK_OUT
echo Apertura dialog per la cartella di OUTPUT...
for /f "delims=" %%O in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "Add-Type -AssemblyName System.Windows.Forms | Out-Null; $f = New-Object System.Windows.Forms.FolderBrowserDialog; $f.Description = 'Dove scrivere i .onnx e .fbin?'; $f.ShowNewFolderButton = $true; if ($f.ShowDialog() -eq 'OK') { Write-Output $f.SelectedPath }"') do set OUT_DIR=%%O

if "%OUT_DIR%"=="" (
    echo [ERRORE] Nessuna cartella di output selezionata.
    pause
    exit /b 1
)

:SET_INDEX
set MODE_FLAG=--index-only
set CHOICE=index
goto DO_RUN

:SET_VOICES
set MODE_FLAG=--voices-only
set CHOICE=voices
goto DO_RUN

:DO_CHOICE
if /I "%CHOICE%"=="index" goto SET_INDEX
if /I "%CHOICE%"=="voices" goto SET_VOICES
goto DO_RUN

REM -----------------------------------------------------------
REM Menu modalita': rileva cosa c'e' nella cartella e propone
REM le opzioni sensate. Default 1 (batch completo).
REM -----------------------------------------------------------
set PTH_COUNT=0
set IDX_COUNT=0
set PT_COUNT=0
for /f %%A in ('dir /b /a-d "%IN_DIR%\*.pth" 2^>nul ^| find /c /v ""') do set PTH_COUNT=%%A
for /f %%A in ('dir /b /a-d "%IN_DIR%\*.index" 2^>nul ^| find /c /v ""') do set IDX_COUNT=%%A
for /f %%A in ('dir /b /a-d "%IN_DIR%\*.pt" 2^>nul ^| find /c /v ""') do set PT_COUNT=%%A

echo.
echo Trovati nella cartella di input:
echo   .pth   = %PTH_COUNT%
echo   .index = %IDX_COUNT%
echo   .pt    = %PT_COUNT%
echo.

set "MODE_FLAG="
echo Modalita' disponibili:
echo   [1] Batch completo  (.pth -^> .onnx  +  .index -^> .fbin  +  .pt -^> .ptl)
echo   [2] Solo index      (.index -^> .fbin, ideale se aggiungi index a modelli gia' convertiti)
echo   [3] Solo voci       (.pth -^> .onnx, .pt -^> .ptl, salta gli index)
echo   [4] Solo HuBERT     (.pt -^> .ptl)
echo.
set /p CHOICE=Modalita' [1/2/3/4, default 1]:

if "%CHOICE%"=="2" set MODE_FLAG=--index-only
if "%CHOICE%"=="3" set MODE_FLAG=--voices-only
if "%CHOICE%"=="4" (
    set MODE_FLAG=--voices-only
    REM Per HuBERT-only filtriamo ulteriormente via glob
)

if "%CHOICE%"=="4" goto HUBERT_ONLY

goto DO_RUN

:HUBERT_ONLY
echo.
echo Copio il/i .pt in una cartella temporanea ed esporto solo quello...
set "TMP_HUBERT=%TEMP%\rvc_hubert_%RANDOM%"
mkdir "%TMP_HUBERT%" 2>nul
copy /Y "%IN_DIR%\*.pt" "%TMP_HUBERT%\" >nul
set IN_DIR=%TMP_HUBERT%
set MODE_FLAG=--voices-only
goto DO_RUN

:DO_RUN
echo.
echo ============================================================
echo   INPUT  : %IN_DIR%
echo   OUTPUT : %OUT_DIR%
echo   MODALITA': !CHOICE! !MODE_FLAG!
echo ============================================================
echo.

if not exist "%IN_DIR%" (
    echo [ERRORE] La cartella di input non esiste: %IN_DIR%
    pause
    exit /b 1
)

"%PYTHON_EXE%" "%SCRIPT%" --in "%IN_DIR%" --out "!OUT_DIR!" !MODE_FLAG!
set RC=%ERRORLEVEL%
echo.
if %RC%==0 (
    echo [OK] Conversione completata. I file si trovano in:
    echo      %OUT_DIR%
) else (
    echo [WARN] La conversione e' terminata con errori (codice %RC%). Vedi log sopra.
)
echo.
pause
exit /b %RC%
