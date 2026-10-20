"""
verify_model_set.py
Controlla che la cartella dei modelli contenga un set completo ONNX valido
per la conversione RVC Android (HuBERT, RMVPE, mel basis, synth, index).

Uso:
  python tools\\verify_model_set.py [cartella]

Default: k:\\AI\\Android\\rvc-portable-android\\Necessari
"""

from __future__ import annotations

import hashlib
import os
import struct
import sys
from pathlib import Path

EXPECTED = {
    "hubert_base.onnx": (377_679_877, "HuBERT ONNX (Hugging Face MidFord327)"),
    "rmvpe.onnx": (361_688_443, "RMVPE ONNX (pitch extraction)"),
    "rmvpe_melfb.bin": (262_656, "RMVPE mel basis (128*513 float32)"),
    "Nicola-Spot.onnx": (115_166_698, "Synth ONNX RVC v2 (Nicola-Spot)"),
    "Nicola-Spot_v2.fbin": (56_801_392, "FAISS IVF index replica (.fbin)"),
}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def check_fbin(path: Path) -> list[str]:
    issues = []
    with path.open("rb") as f:
        magic = f.read(4)
        if magic != b"RVCX":
            issues.append(f"magic {magic!r} != 'RVCX'")
            return issues
        d, ntotal, nlist, nprobe, metric, _reserved = struct.unpack("<iqiiii", f.read(28))
    if d != 768:
        issues.append(f"dim {d} != 768 (richiesto per feature HuBERT)")
    if ntotal <= 0:
        issues.append("ntotal <= 0")
    if nlist <= 0:
        issues.append("nlist <= 0")
    if nprobe <= 0 or nprobe > nlist:
        issues.append(f"nprobe {nprobe} non valido per nlist {nlist}")
    return issues


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(r"k:\AI\Android\rvc-portable-android\Necessari")
    if not root.is_dir():
        print(f"[ERRORE] Cartella non trovata: {root}")
        return 1
    print(f"Verifica set modelli in: {root}")
    print("-" * 60)
    ok = True
    for name, (size, desc) in EXPECTED.items():
        path = root / name
        if not path.is_file():
            print(f"[MANCANTE] {name}: {desc}")
            ok = False
            continue
        actual = path.stat().st_size
        if actual != size:
            print(f"[DIMENSIONE] {name}: {actual} != {size} ({desc})")
            ok = False
            continue
        digest = sha256(path)
        if name.endswith(".fbin"):
            issues = check_fbin(path)
            if issues:
                for issue in issues:
                    print(f"[FBIN] {name}: {issue}")
                ok = False
                continue
        print(f"[OK]      {name}  {size:>12d} B  sha256={digest[:16]}...")
    print("-" * 60)
    if not ok:
        print("Set incompleto o non valido.")
        return 2
    print("Set completo e valido.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
