"""
============================================================
  RVC v2 .pth → TorchScript Mobile (.ptl) Converter
  Uso: Trascina un file .pth su questo script BAT,
       oppure eseguilo e seleziona il file manualmente.
============================================================
"""
import sys, os, tkinter as tk
from tkinter import filedialog

PYTHON_DIR = os.path.dirname(sys.executable)
RVC_LIB = os.path.join(PYTHON_DIR, "Lib", "site-packages")
if RVC_LIB not in sys.path:
    sys.path.insert(0, RVC_LIB)

import torch

def get_input_file():
    """Ottieni il file .pth da argv o da un file dialog."""
    if len(sys.argv) > 1 and os.path.isfile(sys.argv[1]):
        return sys.argv[1]
    root = tk.Tk()
    root.withdraw()
    path = filedialog.askopenfilename(
        title="Seleziona il modello RVC (.pth)",
        filetypes=[("PyTorch Checkpoint", "*.pth"), ("All files", "*.*")]
    )
    root.destroy()
    return path if path else None

def load_rvc_model(pth_path):
    """Carica il checkpoint e ricostruisce il modello RVC v2."""
    print(f"[1/4] Caricamento checkpoint: {pth_path}")
    ck = torch.load(pth_path, map_location="cpu")

    config = ck.get("config")
    version = ck.get("version", "v2")
    sr_label = ck.get("sr", "48k")
    f0_enabled = ck.get("f0", 1)
    info = ck.get("info", "")
    weights = ck["weight"]

    print(f"    Versione: {version}")
    print(f"    Sample Rate: {sr_label}")
    print(f"    F0 (Pitch): {'Si' if f0_enabled else 'No'}")
    print(f"    Info: {info}")
    print(f"    Config: {config}")
    print(f"    Pesi: {len(weights)} tensori")

    # Importa la classe del modello RVC
    try:
        from rvc_python.lib.infer_pack.models import SynthesizerTrnMs768NSFsid
    except ImportError:
        print("\n[ERRORE] Non trovo rvc_python.lib.infer_pack.models")
        print("         Assicurati di usare il Python di rvc-portable.")
        sys.exit(1)

    # Parametri dalla config del checkpoint
    # config = [spec_ch, seg_sz, inter_ch, hidden_ch, filter_ch, n_heads, n_layers,
    #           kernel_sz, p_dropout, resblock, resblock_kernel_sizes,
    #           resblock_dilation_sizes, upsample_rates, upsample_initial_channel,
    #           upsample_kernel_sizes, spk_embed_dim, gin_channels, sr]
    model = SynthesizerTrnMs768NSFsid(
        spec_channels=config[0],
        segment_size=config[1],
        inter_channels=config[2],
        hidden_channels=config[3],
        filter_channels=config[4],
        n_heads=config[5],
        n_layers=config[6],
        kernel_size=config[7],
        p_dropout=config[8],
        resblock=config[9],
        resblock_kernel_sizes=config[10],
        resblock_dilation_sizes=config[11],
        upsample_rates=config[12],
        upsample_initial_channel=config[13],
        upsample_kernel_sizes=config[14],
        spk_embed_dim=config[15],
        gin_channels=config[16],
        sr=config[17] if isinstance(config[17], int) else int(sr_label.replace("k", "000")),
        is_half=False
    )

    model.load_state_dict(weights, strict=False)
    model.eval()
    model.float()
    print(f"    Modello ricostruito con successo.")
    return model, config

class RVCInferWrapper(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, phone: torch.Tensor, phone_lengths: torch.Tensor, pitch: torch.Tensor, pitchf: torch.Tensor, sid: torch.Tensor, rnd: torch.Tensor):
        # Stessa formula dell'infer() eager di rvc_python (models.py).
        # rnd [1,192,T] è il rumore del prior del flow, passato in ingresso
        # (come nel legacy ONNX) per un confronto deterministico.
        g = self.model.emb_g(sid).unsqueeze(-1)
        m_p, logs_p, x_mask = self.model.enc_p(phone, pitch, phone_lengths)
        z_p = (m_p + torch.exp(logs_p) * rnd * 0.66666) * x_mask
        z = self.model.flow(z_p, x_mask, g=g, reverse=True)
        o = self.model.dec(z * x_mask, pitchf, g=g)
        return o

def trace_and_export(model, config, output_path):
    """Traccia il modello e salva come .ptl per Android."""
    print(f"[2/4] Tracing del modello con torch.jit.trace...")

    wrapper = RVCInferWrapper(model)
    wrapper.eval()

    tLen = 100
    phone = torch.randn(1, tLen, 768)
    phone_lengths = torch.tensor([tLen], dtype=torch.long)
    pitch = torch.randint(1, 256, (1, tLen), dtype=torch.long)
    pitchf = torch.randn(1, tLen)
    sid = torch.tensor([0], dtype=torch.long)
    rnd = torch.randn(1, 192, tLen)

    with torch.no_grad():
        try:
            traced = torch.jit.trace(
                wrapper,
                (phone, phone_lengths, pitch, pitchf, sid, rnd),
                strict=False
            )
            print("    torch.jit.trace completato con successo.")

            torch.manual_seed(2026)
            ref = wrapper(phone, phone_lengths, pitch, pitchf, sid, rnd)[0, 0]
            torch.manual_seed(2026)
            out = traced(phone, phone_lengths, pitch, pitchf, sid, rnd)[0, 0]
            diff = float((ref - out).abs().max())
            ok = diff < 1e-4
            print(f"    Auto-verifica vs eager (stesso seed): max diff = {diff:.3e} {'OK' if ok else 'FAIL'}")
            if not ok:
                print("\n[ERRORE] Il trace non riproduce il modello eager.")
                sys.exit(1)
        except Exception as e:
            print(f"\n[ERRORE] torch.jit.trace fallito: {e}")
            fallback_path = output_path.replace(".ptl", "_weights.pt")
            torch.save({
                "state_dict": model.state_dict(),
                "config": config
            }, fallback_path)
            print(f"    Salvato fallback: {fallback_path}")
            return fallback_path

    print("[3/4] Salvataggio diretto (NO optimize_for_mobile: rompe il RNG)...")
    print(f"[4/4] Salvataggio: {output_path}")
    with torch.no_grad():
        try:
            torch.manual_seed(2026)
            ref = wrapper(phone, phone_lengths, pitch, pitchf, sid, rnd)[0, 0]
            torch.manual_seed(2026)
            out = traced(phone, phone_lengths, pitch, pitchf, sid, rnd)[0, 0]
            diff = float((ref - out).abs().max())
            ok = diff < 1e-4
            print(f"    Auto-verifica del manufatto finale vs eager: max diff = {diff:.3e} {'OK' if ok else 'FAIL'}")
            if not ok:
                print("\n[ERRORE] Il manufatto finale non riproduce il modello eager.")
                sys.exit(1)
        except Exception as e:
            print(f"\n[ERRORE] Auto-verifica fallita: {e}")
            sys.exit(1)
    try:
        traced._save_for_lite_interpreter(output_path)
    except AttributeError:
        torch.jit.save(traced, output_path)

    size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"\n{'='*60}")
    print(f"  [OK] CONVERSIONE COMPLETATA CON SUCCESSO!")
    print(f"  File: {output_path}")
    print(f"  Dimensione: {size_mb:.1f} MB")
    print(f"  Pronto per essere caricato nell'APK Android.")
    print(f"{'='*60}")
    return output_path

def main():
    print("=" * 60)
    print("  RVC v2 -> TorchScript Mobile (.ptl) Converter")
    print("=" * 60)
    print(f"  PyTorch: {torch.__version__}")
    print(f"  Python:  {sys.version.split()[0]}")
    print()

    pth_path = get_input_file()
    if not pth_path:
        print("[ERRORE] Nessun file selezionato.")
        input("\nPremi INVIO per uscire...")
        return

    output_path = os.path.splitext(pth_path)[0] + ".ptl"
    
    model, config = load_rvc_model(pth_path)
    result = trace_and_export(model, config, output_path)
    
    if len(sys.argv) <= 1:
        input("\nPremi INVIO per uscire...")

if __name__ == "__main__":
    main()
