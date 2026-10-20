"""
============================================================
  converti_batch.py - Converte in batch una cartella di
  modelli RVC legacy (.pth + .index) in artefatti Android
  moderni (.onnx + .fbin).

  USO:
    K:\\AI\\rvc-portable\\runtime\\python.exe converti_batch.py
        --in   <cartella_input>
        --out  <cartella_output>

  Comportamento:
    - per ogni <name>.pth  ->  <out>/<name>.onnx
    - per ogni <name>.index -> <out>/<name>.fbin
    - HuBERT .pt -> <out>/hubert_base.ptl (per HuBERT legacy)
    - log per file + riepilogo finale

  Il runtime K:\\AI\\rvc-portable è read-only; tutti i file
  vengono letti da --in e scritti in --out.
============================================================
"""
import argparse
import os
import shutil
import sys
import time
import traceback

RUNTIME_PY = r"K:\AI\rvc-portable\runtime\python.exe"
PY_EXE = RUNTIME_PY if os.path.exists(RUNTIME_PY) else sys.executable
PYTHON_DIR = os.path.dirname(PY_EXE) if PY_EXE.lower().endswith("python.exe") else os.path.dirname(sys.executable)
RVC_LIB = os.path.join(PYTHON_DIR, "Lib", "site-packages")
if RVC_LIB not in sys.path:
    sys.path.insert(0, RVC_LIB)


def log(msg, level="info"):
    tag = {"info": "[INFO]", "warn": "[WARN]", "err": "[ERR!]", "ok": "[ OK ]"}.get(level, "[..]")
    print(f"{tag} {msg}", flush=True)


def stem(p):
    return os.path.splitext(os.path.basename(p))[0]


def find_paired_files(in_dir):
    pairs = {"pth": [], "index": [], "pt": []}
    for root, _dirs, files in os.walk(in_dir):
        for f in files:
            ext = os.path.splitext(f)[1].lower()
            if ext == ".pth":
                pairs["pth"].append(os.path.join(root, f))
            elif ext == ".index":
                pairs["index"].append(os.path.join(root, f))
            elif ext == ".pt":
                # Filtra solo i .pt che sembrano HuBERT base (contengono "hubert"
                # oppure "hubert_base") - gli altri sono probabilmente pesi
                # specifici del modello vocale e vanno ignorati.
                if "hubert" in f.lower():
                    pairs["pt"].append(os.path.join(root, f))
    return pairs


def match_index_to_pth(pth_files, index_files):
    """Restituisce dict stem -> [pth, index] per i modelli che hanno entrambi."""
    out = {}
    for pth in pth_files:
        s = stem(pth).lower()
        out.setdefault(s, [None, None])[0] = pth
    for idx in index_files:
        s = stem(idx).lower()
        # tenta match diretto, poi normalizzato
        if s in out:
            out[s][1] = idx
        else:
            # prova a matchare con prefisso più lungo
            best = None
            for k in out:
                if s.startswith(k) or k.startswith(s):
                    if best is None or len(k) > len(best):
                        best = k
            if best is not None and out[best][1] is None:
                out[best][1] = idx
    return {k: v for k, v in out.items() if v[1] is not None}


def run_pth_to_onnx(pth_path, out_path):
    """Delega a tools/convert_pth_to_onnx.py reimportandone la logica."""
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__))))
    import importlib.util
    spec = importlib.util.spec_from_file_location(
        "convert_pth_to_onnx",
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "convert_pth_to_onnx.py"),
    )
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mod.convert_pth(pth_path, out_path)


def run_index_to_fbin(index_path, out_path):
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__))))
    import importlib.util
    spec = importlib.util.spec_from_file_location(
        "export_faiss_bin",
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "export_faiss_bin.py"),
    )
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mod.export_index(index_path, out_path)


def run_pt_to_ptl(pt_path, out_path):
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__))))
    import importlib.util
    spec = importlib.util.spec_from_file_location(
        "export_hubert_ptl",
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "export_hubert_ptl.py"),
    )
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mod.export_hubert(pt_path, out_path)


def main():
    ap = argparse.ArgumentParser(description="Batch RVC legacy -> Android ONNX/FBIN.")
    ap.add_argument("--in", dest="in_dir", required=True, help="Cartella con .pth, .index, .pt")
    ap.add_argument("--out", dest="out_dir", required=True, help="Cartella di destinazione")
    ap.add_argument("--hubert-name", default="hubert_base.ptl", help="Nome file HuBERT in output")
    ap.add_argument("--overwrite", action="store_true", help="Sovrascrivi i file esistenti")
    ap.add_argument("--skip-failed", action="store_true", help="Continua anche in caso di errore")
    ap.add_argument("--index-only", action="store_true",
                    help="Converte solo i .index in .fbin (ignora .pth e .pt)")
    ap.add_argument("--voices-only", action="store_true",
                    help="Converte solo i .pth in .onnx (ignora .index e .pt)")
    args = ap.parse_args()

    in_dir = os.path.abspath(args.in_dir)
    out_dir = os.path.abspath(args.out_dir)
    if not os.path.isdir(in_dir):
        log(f"Cartella di input inesistente: {in_dir}", "err")
        return 1
    os.makedirs(out_dir, exist_ok=True)
    log(f"IN : {in_dir}")
    log(f"OUT: {out_dir}")

    pairs = find_paired_files(in_dir)
    log(f"Trovati {len(pairs['pth'])} .pth, {len(pairs['index'])} .index, {len(pairs['pt'])} .pt")

    matched = match_index_to_pth(pairs["pth"], pairs["index"])
    orphans_index = [i for i in pairs["index"] if not any(stem(i).lower() in k for k in matched)]
    orphans_pth = [p for p in pairs["pth"] if not any(stem(p).lower() in k for k in matched)]

    if matched:
        log(f"Modelli accoppiati: {len(matched)}")
    if orphans_pth:
        log(f"Orfani .pth (senza index): {[os.path.basename(p) for p in orphans_pth]}", "warn")
    if orphans_index:
        log(f"Orfani .index (senza .pth): {[os.path.basename(p) for p in orphans_index]}", "warn")

    results = {"onnx": 0, "fbin": 0, "ptl": 0, "fail": 0}
    do_pth = not args.index_only
    do_index = not args.voices_only
    do_pt = not (args.index_only or args.voices_only)

    if args.index_only:
        log("Modalita': --index-only (converto solo .index -> .fbin)", "info")
    if args.voices_only:
        log("Modalita': --voices-only (converto solo .pth -> .onnx)", "info")

    # 1) .pth -> .onnx
    if do_pth:
        for stem_key, (pth, idx) in matched.items():
            out_onnx = os.path.join(out_dir, f"{stem_key}.onnx")
            if os.path.exists(out_onnx) and not args.overwrite:
                log(f"Skip esistente: {os.path.basename(out_onnx)}")
            else:
                t0 = time.time()
                try:
                    log(f"Convertendo {os.path.basename(pth)} -> .onnx ...")
                    run_pth_to_onnx(pth, out_onnx)
                    dt = time.time() - t0
                    log(f"  -> {os.path.basename(out_onnx)} ({os.path.getsize(out_onnx)/1e6:.1f} MB, {dt:.1f}s)", "ok")
                    results["onnx"] += 1
                except Exception as e:
                    log(f"FALLITO: {os.path.basename(pth)}: {e}", "err")
                    traceback.print_exc()
                    results["fail"] += 1
                    if not args.skip_failed:
                        return 2

        # Anche i .pth orfani (senza index) vanno convertiti.
        for pth in orphans_pth:
            s = stem(pth).lower()
            out_onnx = os.path.join(out_dir, f"{s}.onnx")
            if os.path.exists(out_onnx) and not args.overwrite:
                log(f"Skip esistente: {os.path.basename(out_onnx)}")
                continue
            t0 = time.time()
            try:
                log(f"Convertendo orfano {os.path.basename(pth)} -> .onnx ...")
                run_pth_to_onnx(pth, out_onnx)
                dt = time.time() - t0
                log(f"  -> {os.path.basename(out_onnx)} ({os.path.getsize(out_onnx)/1e6:.1f} MB, {dt:.1f}s)", "ok")
                results["onnx"] += 1
            except Exception as e:
                log(f"FALLITO orfano: {os.path.basename(pth)}: {e}", "err")
                traceback.print_exc()
                results["fail"] += 1
                if not args.skip_failed:
                    return 2

    # 1b) .index accoppiato -> .fbin
    if do_index:
        for stem_key, (pth, idx) in matched.items():
            out_fbin = os.path.join(out_dir, f"{stem_key}.fbin")
            if os.path.exists(out_fbin) and not args.overwrite:
                log(f"Skip esistente: {os.path.basename(out_fbin)}")
            else:
                t0 = time.time()
                try:
                    log(f"Convertendo {os.path.basename(idx)} -> .fbin ...")
                    run_index_to_fbin(idx, out_fbin)
                    dt = time.time() - t0
                    log(f"  -> {os.path.basename(out_fbin)} ({os.path.getsize(out_fbin)/1e6:.1f} MB, {dt:.1f}s)", "ok")
                    results["fbin"] += 1
                except Exception as e:
                    log(f"FALLITO: {os.path.basename(idx)}: {e}", "err")
                    traceback.print_exc()
                    results["fail"] += 1
                    if not args.skip_failed:
                        return 2

    # 2) .pt HuBERT -> .ptl (opzionale)
    if do_pt and pairs["pt"]:
        for pt in pairs["pt"]:
            out_ptl = os.path.join(out_dir, args.hubert_name)
            if os.path.exists(out_ptl) and not args.overwrite:
                log(f"Skip esistente: {args.hubert_name}")
            else:
                t0 = time.time()
                try:
                    log(f"Convertendo {os.path.basename(pt)} -> {args.hubert_name} ...")
                    run_pt_to_ptl(pt, out_ptl)
                    dt = time.time() - t0
                    log(f"  -> {args.hubert_name} ({os.path.getsize(out_ptl)/1e6:.1f} MB, {dt:.1f}s)", "ok")
                    results["ptl"] += 1
                except Exception as e:
                    log(f"FALLITO HuBERT: {e}", "err")
                    results["fail"] += 1
                    if not args.skip_failed:
                        return 2

    # 3) orfani: prova cmq index -> fbin con nome originale
    if do_index:
        for idx in orphans_index:
            out_fbin = os.path.join(out_dir, f"{stem(idx)}.fbin")
            if os.path.exists(out_fbin) and not args.overwrite:
                log(f"Skip esistente: {os.path.basename(out_fbin)}")
                continue
            try:
                log(f"Convertendo orfano {os.path.basename(idx)} -> .fbin ...")
                run_index_to_fbin(idx, out_fbin)
                log(f"  -> {os.path.basename(out_fbin)}", "ok")
                results["fbin"] += 1
            except Exception as e:
                log(f"FALLITO orfano: {os.path.basename(idx)}: {e}", "err")
                traceback.print_exc()
                results["fail"] += 1
                if not args.skip_failed:
                    return 2

    log("-" * 50)
    log(f"Riepilogo: onnx={results['onnx']}, fbin={results['fbin']}, ptl={results['ptl']}, fail={results['fail']}", "ok")
    return 0 if results["fail"] == 0 else 3


if __name__ == "__main__":
    sys.exit(main())
