# RVC-Droid — DAW Android per RVC Portable

Mini-DAW Android per registrare, montare e rifinire spot vocali; dialoga con **RVC Portable** su PC
tramite una cartella **Dropbox** condivisa (`/TestRVC`). Il telefono registra e monta, il PC applica
RVC/mastering pesanti e restituisce le tracce; i parametri e lo stato del PC sono controllabili
dall'app anche da lontano.

## Repository

- Sorgenti: <https://github.com/Nicotina4ever/rvc-droid>
- APK firmato: [RVC-Droid-2.3.apk](https://github.com/Nicotina4ever/rvc-droid/releases/download/v2.1/RVC-Droid-2.3.apk) (Releases v2.3), non versionato.
- **Non versionati**: `Necessari/` (archivio modelli ONNX sperimentali ~1,3 GB), `Dataset_spot/`, build, APK e keystore. I modelli `.pth` restano sul PC: l'app non richiede modelli ONNX.
- Istruzioni pratiche: [ISTRUZIONI.md](ISTRUZIONI.md)
- Firma release: in locale usa `app/release.jks` + eventuale `keystore.properties` (entrambi
  gitignored); su un clone pulito `assembleRelease` produce un APK non firmato e `assembleDebug`
  funziona normalmente.

## Funzionalità

| Area | Cosa include |
|---|---|
| **Studio** | registrazione con meter, backing track, video di riferimento, gobbo rapido, export |
| **Timeline** | editor WAV, trim/silence/cut/copy/paste/undo, player, esportazione regioni |
| **Mixer** | fino a 32 tracce, volume/pan/mute/solo/ducking, timeline zoom/pan, render mono/stereo, LUFS |
| **Video** | player Media3/ExoPlayer sincronizzato con la timeline, riferimento per doppiaggio |
| **Gobbo** | teleprompter full-screen con scroll automatico, specchio, libreria script locale |
| **Plugin rack** | catena master inseribile: gate → EQ voce/musica → compressore → limiter → riverbero → master, bypass per modulo, preset di fabbrica e preset utente salvabili |
| **Cloud** | RVC remoto via Dropbox, bundle progetto `.rvcdaw.zip`, controllo remoto PC (`status.json` + `config.json`) |
| **RVC remoto** | conversione sul PC con i `.pth` originali (PyTorch/CUDA): **nessuna conversione ONNX**, nessun modello da copiare sul telefono |

## Build

Requisiti: JDK 17 (`JAVA_HOME`), Android SDK/NDK in `local.properties` (es. `sdk.dir=F:\\Android\\SDK`).

```bat
gradlew.bat assembleDebug
gradlew.bat assembleRelease
gradlew.bat :app:copyRelease      :: copia l'APK firmato in app-release.snapshot.apk
gradlew.bat testDebugUnitTest
```

Output: `app/build/outputs/apk/debug/app-debug.apk` e
`app/build/outputs/apk/release/app-release.apk` (firmato con `app/release.jks`).

## Flusso con il PC

1. PC: RVC Portable → **Cloud → Rileva Dropbox → Attiva preset** (crea `AudioIn`, `AudioOut`,
   `config.json`, `modelli.txt`, `status.json`).
2. Telefono: **Cloud → RVC remoto**, inserisci App Key/token Dropbox, sincronizza `modelli.txt`.
3. Telefono: registra/monta → su una clip **CONVERTI CON RVC**; il risultato rientra da
   `AudioOut/<stesso nome>.wav`.
4. Telefono: **Cloud → Controllo PC** per vedere ONLINE/OFFLINE, coda, modello e per inviare nuovi
   parametri al PC (pitch, metodo F0, index rate, protezione, formattazione).
5. Telefono: **Cloud → Progetti** per caricare/scaricare il bundle completo del progetto su
   `/TestRVC/Projects` (continui il montaggio su un altro dispositivo o archivi sul PC).

Nessuna porta del PC è esposta: il dialogo passa solo dal client Dropbox desktop.

## Struttura

```text
app/src/main/java/com/rvc/droid/
├─ ui/                 # schermate DAW (studio, editor, mixer, video, prompter, cloud)
├─ ui/components/      # design system condiviso (RvcUI, meter, waveform, pannelli FX)
├─ ui/theme/           # palette allineata a RVC Portable PC (navy/indigo)
├─ ui/plugins/         # plugin rack + preset di fabbrica/utente
├─ ui/project/         # ProjectManager, ProjectBundle (.rvcdaw.zip)
├─ cloud/              # DropboxClient, stato PC (status.json)
├─ audio/              # WavEditor, WavPlayer, NativeAudioBridge
├─ services/           # foreground service audio
└─ cpp/                # motore RVC ONNX + TrackMixer nativo
```

I modelli (`*.pth`) **non sono versionati**: restano sul PC e vengono usati direttamente da RVC Portable. L'app non richiede `.onnx`; `Necessari/` è solo un archivio storico della pipeline ONNX locale (disattivata). Vedi `AGENTS.md` per i vincoli del motore e la cronologia delle build.
