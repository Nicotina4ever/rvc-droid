"""
============================================================
  RVC Hubert Base (.pt) → TorchScript Mobile (.ptl) Exporter
  Estrae le feature fonetiche (phone [1,T,768]) per RVC v2.
  Uso: Trascina un file .pt su questo script BAT,
       oppure eseguilo e seleziona il file manualmente.
  Output: Modelli/Modelli/hubert_base.ptl (MAI in rvc-portable).
============================================================
"""
import sys, os, tkinter as tk
from tkinter import filedialog

PYTHON_DIR = os.path.dirname(sys.executable)
RVC_LIB = os.path.join(PYTHON_DIR, "Lib", "site-packages")
if RVC_LIB not in sys.path:
    sys.path.insert(0, RVC_LIB)

import torch
import rvc_python


class HubertMobile(torch.nn.Module):
    """RVC v2 phone extractor: conv feature extractor + 12 layer transformer.
    Output: [1, T, 768] con T = N//320 - 1 frame per N campioni audio.
    Nessuna branch Python su tensor: tracciabile e generalizza la lunghezza.
    """

    def __init__(self, m):
        super().__init__()
        self.feature_extractor = m.feature_extractor
        self.layer_norm = m.layer_norm
        self.post_extract_proj = m.post_extract_proj
        self.dropout_input = m.dropout_input
        enc = m.encoder
        self.pos_conv = enc.pos_conv
        self.encoder_layer_norm = enc.layer_norm
        self.layers = enc.layers

    def forward(self, source):
        x = self.feature_extractor(source)
        x = x.transpose(1, 2)
        x = self.layer_norm(x)
        x = self.post_extract_proj(x)
        x = self.dropout_input(x)
        x_conv = self.pos_conv(x.transpose(1, 2))
        x = x + x_conv.transpose(1, 2)
        x = self.encoder_layer_norm(x)
        x = x.transpose(0, 1)
        for layer in self.layers:
            x, _ = layer(x)
        x = x.transpose(0, 1)
        return x


def get_input_file(default_path):
    """Ottieni il file .pt da argv, dal default di rvc_python o da dialog."""
    if len(sys.argv) > 1 and os.path.isfile(sys.argv[1]):
        return sys.argv[1]
    if os.path.isfile(default_path):
        print(f"    Usato hubert predefinito: {default_path}")
        return default_path
    root = tk.Tk()
    root.withdraw()
    path = filedialog.askopenfilename(
        title="Seleziona il modello Hubert (.pt)",
        initialdir=os.path.dirname(default_path) if os.path.exists(default_path) else None,
        filetypes=[("PyTorch Checkpoint", "*.pt"), ("All files", "*.*")]
    )
    root.destroy()
    return path if path else None


def load_hubert(hubert_path):
    """Carica il modello Hubert di rvc_python e lo prepara per l'inferenza."""
    print(f"[1/4] Caricamento Hubert: {hubert_path}")
    from rvc_python.lib.jit.get_hubert import get_hubert_model
    model = get_hubert_model(hubert_path, torch.device("cpu"))
    model.eval()
    model.float()
    model.feature_grad_mult = 0.0
    n_params = sum(p.numel() for p in model.parameters())
    print(f"    Modello caricato ({n_params/1e6:.1f}M parametri).")
    return model


def reference_features(model, source):
    """Feature di riferimento: identico al path usato da rvc_python per v2
    (output_layer=12, nessun final_proj)."""
    feats, _ = model.extract_features(source=source, padding_mask=None, output_layer=12)
    return feats


def max_diff(a, b):
    return float((a - b).abs().max())


def validate_eager(mobile, model):
    """Confronta HubertMobile (eager) con la reference rvc_python."""
    print("[2/4] Validazione eager vs rvc_python reference...")
    lengths = [16000 * 2, 16000 * 5, 16000 * 10]
    worst = 0.0
    with torch.no_grad():
        for n in lengths:
            src = torch.randn(1, n) * 0.1
            ref = reference_features(model, src)
            out = mobile(src)
            d = max_diff(ref, out)
            worst = max(worst, d)
            exp_t = n // 320 - 1
            ok = (out.shape == ref.shape == (1, exp_t, 768)) and d < 1e-3
            status = "OK" if ok else "MISMATCH"
            print(f"    {n//16000:2d}s -> [1,{out.shape[1]},768]  diff={d:.2e}  {status}")
    if worst >= 1e-3:
        print("\n[ERRORE] HubertMobile non corrisponde alla reference.")
        sys.exit(1)
    return worst


def trace_and_export(mobile, output_path):
    """Traccia con lunghezza libera e salva come .ptl (lite interpreter)."""
    print(f"[3/4] Tracing con torch.jit.trace (input 5s)...")
    source = torch.randn(1, 16000 * 5) * 0.1
    with torch.no_grad():
        traced = torch.jit.trace(mobile, source, strict=False)
    print("    Trace completato. Salvataggio lite interpreter...")
    traced._save_for_lite_interpreter(output_path)
    print(f"    Salvato: {output_path} ({os.path.getsize(output_path)/1e6:.1f} MB)")


def validate_ptl(mobile, output_path):
    """Ricarica il .ptl e verifica lunghezze dinamiche vs eager."""
    print("[4/4] Validazione .ptl (lite interpreter, lunghezze dinamiche)...")
    loaded = torch.jit.load(output_path)
    loaded.eval()
    lengths = [16000 * 2, 16000 * 5, 16000 * 12]
    worst = 0.0
    with torch.no_grad():
        for n in lengths:
            src = torch.randn(1, n) * 0.1
            out = loaded(src)
            ref = mobile(src)
            d = max_diff(ref, out)
            worst = max(worst, d)
            exp_t = n // 320 - 1
            ok = out.shape == (1, exp_t, 768) and d < 1e-4
            status = "OK" if ok else "MISMATCH"
            print(f"    {n//16000:2d}s -> [1,{out.shape[1]},768]  diff={d:.2e}  {status}")
    if worst >= 1e-4:
        print("\n[ERRORE] Il .ptl non riproduce l'eager su lunghezze non tracciate.")
        sys.exit(1)
    return worst


def main():
    print("=" * 60)
    print("  RVC Hubert Base -> TorchScript Mobile (.ptl) Exporter")
    print("=" * 60)
    print(f"  PyTorch: {torch.__version__}")
    print(f"  Python:  {sys.version.split()[0]}")
    print()

    default_path = os.path.join(os.path.dirname(rvc_python.__file__), "base_model", "hubert_base.pt")
    hubert_path = get_input_file(default_path)
    if not hubert_path:
        print("[ERRORE] Nessun file selezionato.")
        input("\nPremi INVIO per uscire...")
        return

    output_path = os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "..", "Modelli", "Modelli",
        os.path.splitext(os.path.basename(hubert_path))[0] + ".ptl",
    )
    output_path = os.path.abspath(output_path)

    model = load_hubert(hubert_path)
    mobile = HubertMobile(model)
    mobile.eval()

    validate_eager(mobile, model)
    del model
    torch.cuda.empty_cache() if torch.cuda.is_available() else None

    trace_and_export(mobile, output_path)
    validate_ptl(mobile, output_path)

    size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"\n{'='*60}")
    print(f"  [OK] EXPORT COMPLETATO CON SUCCESSO!")
    print(f"  File: {output_path}")
    print(f"  Dimensione: {size_mb:.1f} MB")
    print(f"  Pronto per essere caricato nell'APK Android.")
    print(f"{'='*60}")
    if len(sys.argv) <= 1:
        try:
            input("\nPremi INVIO per uscire...")
        except EOFError:
            pass


def export_hubert(hubert_path, out_path=None):
    """Entry point riusabile: produce un .ptl accanto al .pt (o in out_path)."""
    model = load_hubert(hubert_path)
    mobile = HubertMobile(model)
    mobile.eval()

    if out_path is None or os.path.isdir(out_path):
        out_dir = out_path or os.path.dirname(os.path.abspath(hubert_path))
        out_path = os.path.join(out_dir, os.path.splitext(os.path.basename(hubert_path))[0] + ".ptl")
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)

    validate_eager(mobile, model)
    del model
    torch.cuda.empty_cache() if torch.cuda.is_available() else None

    trace_and_export(mobile, out_path)
    validate_ptl(mobile, out_path)
    return out_path


if __name__ == "__main__":
    main()
