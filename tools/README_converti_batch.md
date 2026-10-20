# CONVERTI_BATCH - Conversione batch modelli RVC legacy → Android

Converti un'intera cartella di `.pth` + `.index` (modello RVC legacy v1/v2) in
artefatti Android moderni (`.onnx` + `.fbin`) con un solo click.

## Avvio rapido (doppio click)

1. Doppio click su `tools/CONVERTI_BATCH.bat`.
2. Si apre un dialog Windows per la **cartella di INPUT** (quella con i tuoi
   `.pth` e `.index`). Selezionala e premi OK.
3. Si apre un dialog per la **cartella di OUTPUT** (dove scrivere `.onnx` e
   `.fbin`). Selezionala (o creane una nuova) e premi OK.
4. Il BAT rileva quanti `.pth`, `.index`, `.pt` ci sono e propone un menu:
   ```
   [1] Batch completo  (.pth -> .onnx  +  .index -> .fbin  +  .pt -> .ptl)
   [2] Solo index      (.index -> .fbin, ideale se aggiungi index a modelli gia' convertiti)
   [3] Solo voci       (.pth -> .onnx, .pt -> .ptl, salta gli index)
   [4] Solo HuBERT     (.pt -> .ptl)
   ```
5. Scegli la modalità (default `1`) e attendi il riepilogo finale.

## Drag & drop (singolo file o cartella)

Trascina uno o più file `.pth`/`.index`/`.pt` direttamente sul BAT:
ti chiede solo la cartella di output e usa la cartella del file come input.

## Modalità CLI (no dialog)

```
K:\AI\rvc-portable\runtime\python.exe tools\converti_batch.py ^
    --in   K:\Modelli\Legacy ^
    --out  K:\Modelli\Android

# Solo index (utile se aggiungi un nuovo .index a un modello gia' convertito)
K:\AI\rvc-portable\runtime\python.exe tools\converti_batch.py ^
    --in   K:\Modelli\SoloIndex ^
    --out  K:\Modelli\Android --index-only

# Solo voci (salta gli index)
K:\AI\rvc-portable\runtime\python.exe tools\converti_batch.py ^
    --in   K:\Modelli\SoloPth ^
    --out  K:\Modelli\Android --voices-only
```

Oppure via BAT:

```
CONVERTI_BATCH.bat K:\Modelli\Legacy K:\Modelli\Android
CONVERTI_BATCH.bat K:\Modelli\SoloIndex K:\Modelli\Android index
CONVERTI_BATCH.bat K:\Modelli\SoloPth K:\Modelli\Android voices
CONVERTI_BATCH.bat -folder K:\Modelli\Legacy K:\Modelli\Android index
```

## Cosa viene prodotto

| Input                | Output                                | Note                                                |
|----------------------|---------------------------------------|-----------------------------------------------------|
| `Nome.pth`           | `nome.onnx`                           | Modello vocale v2, opset 17, dinamico su T          |
| `Nome.index`         | `nome.fbin` (accoppiato) **o** `<index>.fbin` (orfano) | Indice faiss IVF-Flat serializzato per replica C++ |
| `hubert_base.pt`     | `hubert_base.ptl`                     | HuBERT in TorchScript Mobile (legacy)               |

## Matching .pth ↔ .index

Il tool abbina un `.pth` al suo `.index` confrontando lo **stem** del nome
file (case-insensitive). Esempio:

- `Nicola-Spot.pth`  ↔  `added_IVF461_Flat_nprobe_1_Nicola-Spot_v2.index`  ✓
- `LoZio.pth`        ↔  (nessun index corrispondente)  ⚠ orfano

Gli orfani vengono comunque processati:

- `.pth` orfano → produce `.onnx` con lo stesso stem
- `.index` orfano → produce `.fbin` con lo stem dell'index (es. `added_IVF461_Flat_nprobe_1_Nicola-Spot_v2.fbin`)

## Opzioni aggiuntive

- `--overwrite` riscrive anche i file già presenti in `--out`.
- `--skip-failed` prosegue dopo un errore (utile se un modello è corrotto
  e non blocca l'intero batch).
- `--index-only` salta `.pth` e `.pt`, converte solo `.index`.
- `--voices-only` salta `.index`, converte `.pth` e `.pt`.
- `--hubert-name <nome>` sceglie il nome del file HuBERT in output
  (default `hubert_base.ptl`).

## Requisiti

- `K:\AI\rvc-portable\runtime\python.exe` (read-only, contiene torch, faiss, onnx, onnxsim, av).
- `tools/convert_pth_to_onnx.py`, `tools/export_faiss_bin.py`, `tools/export_hubert_ptl.py`.

## Note

- L'encoder ONNX usa opset 17 (richiesto da ONNX Runtime 1.17.x).
- L'encoder `onnxsim` non è strettamente necessario ma riduce la dimensione
  del file; installabile con `pip install onnxsim` se non già presente.
- Il tool NON supporta `.ptl` HuBERT pre-generato: se hai già un `.ptl`
  nella cartella di input, viene semplicemente saltato (l'input atteso è
  un `.pt` Hugging Face, non un `.ptl` esistente).
