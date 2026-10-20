"""
============================================================
  convert_pth_to_onnx.py - Converte un modello RVC v2 (.pth)
  in un ONNX compatibile con il backend Android (InferenceEngine.cpp).

  Vincoli del backend C++ (NON modificabili):
    - input : phone [1, T, 768] float32
              phone_lengths [1] int64
              pitch     [1, T] int64
              pitchf    [1, T] float32
              ds        [1] int64
              rnd       [1, 192, T] float32
    - output: audio [1, 1, samples] float32

  USI:
    Trascina un .pth su CONVERTI_PTH_A_ONNX.bat
    oppure
    runtime\\python.exe convert_pth_to_onnx.py <path.pth> [output_dir]

  Il modello Python usato e' rvc_python.lib.infer_pack.models_onnx
  (gia' presente nel runtime K:\\AI\\rvc-portable, non toccato).
============================================================
"""
import os
import sys
import argparse
import struct
import tkinter as tk
from tkinter import filedialog

# Forza il path del runtime (K:\AI\rvc-portable è read-only)
RUNTIME_PY = r"K:\AI\rvc-portable\runtime\python.exe"
if not os.path.exists(RUNTIME_PY):
    RUNTIME_PY = sys.executable

PYTHON_DIR = os.path.dirname(RUNTIME_PY) if RUNTIME_PY.endswith("python.exe") else os.path.dirname(sys.executable)
RVC_LIB = os.path.join(PYTHON_DIR, "Lib", "site-packages")
if RVC_LIB not in sys.path:
    sys.path.insert(0, RVC_LIB)

import torch
import onnx
try:
    from onnxsim import simplify
    HAS_ONNXSIM = True
except ImportError:
    HAS_ONNXSIM = False
    print("[pth->onnx] onnxsim non disponibile, skip semplificazione", flush=True)

# Costanti allineate al C++.
K_FEAT_SIZE = 768
K_RND_DIM = 192


def log(msg):
    print(f"[pth->onnx] {msg}", flush=True)


def get_input_file(arg_path):
    if arg_path and os.path.isfile(arg_path):
        return arg_path
    root = tk.Tk()
    root.withdraw()
    path = filedialog.askopenfilename(
        title="Seleziona il modello RVC (.pth)",
        filetypes=[("PyTorch Checkpoint", "*.pth"), ("All files", "*.*")],
    )
    root.destroy()
    return path or None


def load_checkpoint(pth_path):
    """Carica un checkpoint RVC v2 e ritorna (state_dict, version, sr, f0, info, config, embed_dim)."""
    log(f"Caricamento checkpoint: {pth_path}")
    ck = torch.load(pth_path, map_location="cpu", weights_only=False)
    version = ck.get("version", "v2")
    sr_label = ck.get("sr", "48k")
    f0 = ck.get("f0", 1)
    info = ck.get("info", "")
    config = ck.get("config")
    weights = ck["weight"]

    # Spesso i checkpoint hanno "weight.emb_g.weight" invece di "emb_g.weight".
    # Gestisci i due prefissi più comuni.
    state_dict = {}
    for k, v in weights.items():
        for prefix in ("module.", "model.", "weight."):
            if k.startswith(prefix):
                k = k[len(prefix):]
                break
        state_dict[k] = v
    log(f"  versione={version} sr={sr_label} f0={f0} info={info[:40]}")
    log(f"  chiavi: {len(state_dict)}  esempio: {next(iter(state_dict.keys()))}")

    # Determina spk_embed_dim (numero speaker) e gin_channels dalla shape di emb_g.weight.
    # emb_g.weight ha shape (spk_embed_dim, gin_channels).
    spk_embed_dim = 1
    gin_channels = 256
    if "emb_g.weight" in state_dict:
        emb_g = state_dict["emb_g.weight"]
        spk_embed_dim = emb_g.shape[0]
        gin_channels = emb_g.shape[1]
    # Se gin_channels non combacia con la shape di dec.cond.weight, fidati del cond.
    if "dec.cond.weight" in state_dict:
        gin_channels = state_dict["dec.cond.weight"].shape[1]
    log(f"  spk_embed_dim={spk_embed_dim} gin_channels={gin_channels}")
    return state_dict, version, sr_label, f0, info, config, spk_embed_dim, gin_channels


def build_model(version, sr_label, spk_embed_dim, gin_channels, config=None, is_half=False):
    """Costruisce SynthesizerTrnMsNSFsidM con i parametri della v2 RVC (768 hidden)."""
    from rvc_python.lib.infer_pack.models_onnx import SynthesizerTrnMsNSFsidM

    # Parametri standard per la v2 (768). Per la v1 (256) ci sono defaults diversi,
    # ma qui supportiamo v2 perché é quella richiesta dal backend Android (kFeatSize=768).
    if config is not None and len(config) >= 14:
        # Il config RVC in genere è:
        # [32, 4, 256, 768, 32, 4, 4, 4, "1", [3,7,11], [[1,3,5],[1,3,5],[1,3,5]],
        #  [10,10,2,2], 512, [20,20,4,4], 0.1]
        spec_ch = int(config[0]) if isinstance(config[0], (int, float)) else 1025
        seg_sz = int(config[1]) if isinstance(config[1], (int, float)) else 32
        inter_ch = int(config[2]) if isinstance(config[2], (int, float)) else 192
        hidden_ch = int(config[3]) if isinstance(config[3], (int, float)) else 768
        filter_ch = int(config[4]) if isinstance(config[4], (int, float)) else 768
        n_heads = int(config[5]) if isinstance(config[5], (int, float)) else 2
        n_layers = int(config[6]) if isinstance(config[6], (int, float)) else 6
        kernel_sz = int(config[7]) if isinstance(config[7], (int, float)) else 3
        p_drop = float(config[8]) if isinstance(config[8], (int, float)) else 0.1
        resblock = str(config[9]) if isinstance(config[9], str) else "1"
        resblock_ks = config[10] if isinstance(config[10], list) else [3, 7, 11]
        resblock_ds = config[11] if isinstance(config[11], list) else [[1, 3, 5]] * 3
        up_rates = config[12] if isinstance(config[12], list) else [10, 10, 2, 2]
        up_init_ch = int(config[13]) if isinstance(config[13], (int, float)) else 512
        up_ks = config[14] if len(config) > 14 and isinstance(config[14], list) else [20, 20, 4, 4]
    else:
        # Defaults RVC v2
        spec_ch, seg_sz = 1025, 32
        inter_ch, hidden_ch, filter_ch = 192, 192, 768
        n_heads, n_layers, kernel_sz, p_drop = 2, 6, 3, 0.1
        resblock = "1"
        resblock_ks = [3, 7, 11]
        resblock_ds = [[1, 3, 5], [1, 3, 5], [1, 3, 5]]
        up_rates = [10, 10, 2, 2]
        up_init_ch = 512
        up_ks = [20, 20, 4, 4]

    gin_channels = max(1, int(gin_channels))

    model = SynthesizerTrnMsNSFsidM(
        spec_channels=spec_ch,
        segment_size=seg_sz,
        inter_channels=inter_ch,
        hidden_channels=hidden_ch,
        filter_channels=filter_ch,
        n_heads=n_heads,
        n_layers=n_layers,
        kernel_size=kernel_sz,
        p_dropout=p_drop,
        resblock=resblock,
        resblock_kernel_sizes=resblock_ks,
        resblock_dilation_sizes=resblock_ds,
        upsample_rates=up_rates,
        upsample_initial_channel=up_init_ch,
        upsample_kernel_sizes=up_ks,
        spk_embed_dim=spk_embed_dim,
        gin_channels=gin_channels,
        sr=sr_label,
        version=version,
        is_half=is_half,
    )
    return model


def load_state(model, state_dict):
    """Prova a caricare strict=False e ritorna il numero di chiavi mancanti/extra."""
    own = model.state_dict()
    new = {}
    for k, v in state_dict.items():
        if k in own and own[k].shape == v.shape:
            new[k] = v
    missing, unexpected = model.load_state_dict(new, strict=False)
    return len(missing), len(unexpected), missing[:5], unexpected[:5]


def export_onnx(model, pth_path, output_dir, opset=17):
    """Esporta il modello in ONNX con i nomi/shape del backend C++ Android."""
    base_name = os.path.splitext(os.path.basename(pth_path))[0]
    onnx_path = os.path.join(output_dir, f"{base_name}.onnx")
    T = 100  # frames dummy

    device = next(model.parameters()).device
    phone = torch.randn(1, T, K_FEAT_SIZE, device=device)
    phone_lengths = torch.tensor([T], dtype=torch.long, device=device)
    pitch = torch.randint(low=0, high=255, size=(1, T), dtype=torch.long, device=device)
    pitchf = torch.randn(1, T, device=device)
    ds = torch.tensor([0], dtype=torch.long, device=device)
    rnd = torch.randn(1, K_RND_DIM, T, device=device)

    model.eval()
    model.remove_weight_norm()

    log(f"torch.onnx.export -> {onnx_path} (opset {opset})")
    torch.onnx.export(
        model,
        (phone, phone_lengths, pitch, pitchf, ds, rnd),
        onnx_path,
        input_names=["phone", "phone_lengths", "pitch", "pitchf", "ds", "rnd"],
        output_names=["audio"],
        dynamic_axes={
            "phone": {1: "T"},
            "pitch": {1: "T"},
            "pitchf": {1: "T"},
            "rnd": {2: "T"},
            "audio": {2: "samples"},
        },
        opset_version=opset,
        do_constant_folding=True,
    )
    return onnx_path


def simplify_onnx(onnx_path):
    """Semplifica il grafo ONNX con onnxsim. Sovrascrive in-place."""
    if not HAS_ONNXSIM:
        log(f"onnxsim non disponibile, skip: {onnx_path}")
    else:
        log(f"onnxsim: {onnx_path}")
        model_onnx = onnx.load(onnx_path)
        model_simp, ok = simplify(model_onnx)
        if ok:
            onnx.save(model_simp, onnx_path)
            log("  simplificato OK")
        else:
            log("  simplify() ha restituito False, lascio l'originale")
    onnx.checker.check_model(onnx_path)
    log("  onnx.checker.check_model: OK")


def main():
    parser = argparse.ArgumentParser(description="Converte .pth RVC in .onnx per Android")
    parser.add_argument("pth", nargs="?", help="File .pth (opzionale, apre dialog)")
    parser.add_argument("--out", help="Directory di output", default=None)
    parser.add_argument("--opset", type=int, default=17, help="Opset ONNX (default 17)")
    args = parser.parse_args()

    pth_path = get_input_file(args.pth)
    if not pth_path:
        log("Nessun file selezionato, esco.")
        return 1

    if not pth_path.lower().endswith(".pth"):
        log(f"ATTENZIONE: l'estensione non e' .pth: {pth_path}")

    output_dir = args.out or os.path.dirname(os.path.abspath(pth_path))
    os.makedirs(output_dir, exist_ok=True)

    state_dict, version, sr_label, f0, info, config, spk_embed_dim, gin_channels = load_checkpoint(pth_path)
    model = build_model(version, sr_label, spk_embed_dim, gin_channels, config=config, is_half=False)
    miss, unx, miss_sample, unx_sample = load_state(model, state_dict)
    log(f"  state_dict: {miss} chiavi mancanti, {unx} inattese")
    if miss_sample:
        log(f"    es. mancanti: {miss_sample}")
    if unx_sample:
        log(f"    es. inattese: {unx_sample}")

    onnx_path = export_onnx(model, pth_path, output_dir, opset=args.opset)
    simplify_onnx(onnx_path)

    size_mb = os.path.getsize(onnx_path) / (1024 * 1024)
    log(f"OK: {onnx_path}  ({size_mb:.1f} MB)")
    return 0


def convert_pth(pth_path, out_path=None, opset=17):
    """Entry point riusabile: produce un .onnx accanto al .pth (o in out_path)."""
    state_dict, version, sr_label, f0, info, config, spk_embed_dim, gin_channels = load_checkpoint(pth_path)
    model = build_model(version, sr_label, spk_embed_dim, gin_channels, config=config, is_half=False)
    miss, unx, miss_sample, unx_sample = load_state(model, state_dict)
    log(f"  state_dict: {miss} chiavi mancanti, {unx} inattese")
    if miss_sample:
        log(f"    es. mancanti: {miss_sample}")
    if unx_sample:
        log(f"    es. inattese: {unx_sample}")

    if out_path is None or os.path.isdir(out_path):
        output_dir = out_path or os.path.dirname(os.path.abspath(pth_path))
    else:
        output_dir = os.path.dirname(os.path.abspath(out_path))
    os.makedirs(output_dir, exist_ok=True)

    onnx_path = export_onnx(model, pth_path, output_dir, opset=opset)
    if out_path and not os.path.isdir(out_path):
        os.replace(onnx_path, out_path)
        onnx_path = out_path
    simplify_onnx(onnx_path)
    return onnx_path


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:
        import traceback
        traceback.print_exc()
        log(f"ERRORE: {e}")
        sys.exit(2)
