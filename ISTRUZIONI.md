# Istruzioni — RVC-Droid

Repo: <https://github.com/Nicotina4ever/rvc-droid> · APK: [release v2.3](https://github.com/Nicotina4ever/rvc-droid/releases/tag/v2.1)

## Installare

Scarica `RVC-Droid-2.3.apk` dalla release e installalo. Non servono modelli sul telefono: la
conversione RVC avviene sul PC con i `.pth` originali. Sul PC tieni RVC Portable con il preset
Dropbox attivo (`AudioIn`, `AudioOut`, `config.json`, `modelli.txt`, `status.json`).

## Sviluppare

Requisiti: JDK 17, Android SDK/NDK in `local.properties`.

```bat
set JAVA_HOME=F:\Android\jdk-17.0.12
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:assembleDebug
gradlew.bat :app:assembleRelease :app:copyRelease
```

L'APK firmato locale usa `app/release.jks` (gitignored) e va allegato alle GitHub Releases, non
committato. Su un clone pulito `assembleRelease` genera un APK non firmato: è normale.

## RVC locale ONNX

**Disattivato** (`RvcFeatures.LOCAL_ONNX_ENABLED = false`). La conversione avviene sul PC; non
serve convertire `.pth → .onnx` né copiare modelli sul telefono. Il codice nativo resta nel repo
per non rischiare regressioni, ma non è raggiungibile dall'interfaccia.

## Aggiornare il repository

Il modo semplice è un solo comando (o doppio click su `PUBBLICA.bat`):

```bat
PUBBLICA.bat
```

Con un messaggio tuo:

```bat
PUBBLICA.bat -Message "Descrizione modifica"
```

Lo script fa `git add -A`, commit (solo se ci sono modifiche) e push in un'unica richiesta; usa il
token già salvato, quindi non dovrebbe chiedere permessi ogni volta. Per vedere cosa farebbe senza
pubblicare: `PUBBLICA.bat -DryRun`.

La prima push autentica GitHub nel browser; se chiede permessi ripetuti, chiudi le finestre di
login e fai un solo comando alla volta (oppure usa GitHub Desktop).

Sotto il cofano:

```bat
git add -A
git commit -m "Descrizione modifica"
git push
```

Non committare: `build/`, `.gradle/`, `local.properties`, `*.apk`, `app/release.jks`,
`Necessari/`, `Dataset_spot/`, `keystore.properties`, token o password.

## Pubblicare un nuovo APK

1. incrementa `versionCode` e `versionName` in `app/build.gradle`;
2. esegui `gradlew.bat :app:testDebugUnitTest :app:assembleRelease :app:copyRelease`;
3. crea una release GitHub con tag `vX.Y` e allega `app-release.snapshot.apk` rinominato
   `RVC-Droid-X.Y.apk`;
4. aggiorna il link APK in `README.md`.

Istruzioni più dettagliate (PC + Git + release): [ISTRUZIONI.md del repo PC](https://github.com/Nicotina4ever/rvc-portable/blob/main/ISTRUZIONI.md).
