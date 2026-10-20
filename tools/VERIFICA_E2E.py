"""
============================================================
  VERIFICA_E2E.py - Verifica end-to-end della catena Android
  Confronta la catena mobile (.ptl hubert + .ptl synth + index
  faiss + rmvpe.onnx) con la reference rvc_python su stessi
  input. Il synth usa rnd esplicito (come il legacy ONNX) per
  confronto deterministico. K:\AI\rvc-portable SOLO IN LETTURA.
  Output (wav e report): tools\out\
============================================================
"""
import os
import sys
import numpy as np
import torch
import torch.nn.functional as F

PYTHON_DIR = os.path.dirname(sys.executable)
RVC_LIB = os.path.join(PYTHON_DIR, "Lib", "site-packages")
if RVC_LIB not in sys.path:
    sys.path.insert(0, RVC_LIB)

import faiss
import onnxruntime as ort
from scipy import signal
from scipy.io import wavfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIR = os.path.join(REPO, "Modelli", "Modelli")
if not os.path.exists(os.path.join(MODEL_DIR, "rmvpe.onnx")):
    MODEL_DIR = os.path.join(REPO, "da_eliminare", "Modelli", "Modelli")
OUT_DIR = os.path.join(REPO, "tools", "out")
os.makedirs(OUT_DIR, exist_ok=True)

PTH = os.path.join(MODEL_DIR, "Nicola-Spot.pth")
HUPTL = os.path.join(MODEL_DIR, "hubert_base.ptl")
SYNPTL = os.path.join(MODEL_DIR, "Nicola-Spot.ptl")
INDEX = os.path.join(MODEL_DIR, "added_IVF461_Flat_nprobe_1_Nicola-Spot_v2.index")
RMVPE_ONNX = os.path.join(MODEL_DIR, "rmvpe.onnx")

SR = 48000
SR16 = 16000
WINDOW = 160
T_PAD = SR16
T_PAD_TGT = SR
INDEX_RATE = 0.5
PROTECT = 0.33
SEED = 2026
CENTS_BASE = 20 * np.arange(360) + 1997.3794084376191


class SynthRnd(torch.nn.Module):
    """Wrapper eager con rnd esplicito, identico al trace del .ptl."""

    def __init__(self, net_g):
        super().__init__()
        self.net_g = net_g

    def forward(self, phone, phone_lengths, pitch, pitchf, sid, rnd):
        g = self.net_g.emb_g(sid).unsqueeze(-1)
        m_p, logs_p, x_mask = self.net_g.enc_p(phone, pitch, phone_lengths)
        z_p = (m_p + torch.exp(logs_p) * rnd * 0.66666) * x_mask
        z = self.net_g.flow(z_p, x_mask, g=g, reverse=True)
        o = self.net_g.dec(z * x_mask, pitchf, g=g)
        return o


def make_test_wav(path, seconds=6.0, sr=SR):
    t = np.arange(int(seconds * sr)) / sr
    notes = [220.0, 261.63, 329.63, 392.0]
    seg = int(seconds * sr) // len(notes)
    freq = np.zeros_like(t)
    for i, f in enumerate(notes):
        freq[i * seg:(i + 1) * seg] = f
    freq = freq + 4.0 * np.sin(2 * np.pi * 5.5 * t)
    x = np.zeros_like(t)
    for h in range(1, 9):
        x += (1.0 / h) * np.sin(2 * np.pi * h * np.cumsum(freq) / sr)
    env = 0.5 * (1 - np.cos(2 * np.pi * np.linspace(0, 1, len(t))))
    x = x * env
    x = x + 0.005 * np.random.default_rng(0).standard_normal(len(t))
    x = x / np.abs(x).max() * 0.9
    wavfile.write(path, sr, (x * 32767).astype(np.int16))
    return path


def to_int16(a):
    am = np.abs(a).max() / 0.99
    mi = 32768
    if am > 1:
        mi /= am
    return (a * mi).astype(np.int16)


def decode_onnx_f0(hidden, thred=0.03):
    cents_mapping = np.pad(CENTS_BASE, (4, 4))
    salience = hidden.astype(np.float64)
    center = np.argmax(salience, axis=1) + 4
    sal = np.pad(salience, ((0, 0), (4, 4)))
    t = np.arange(sal.shape[0])
    win = np.stack([sal[i, center[i] - 4:center[i] + 5] for i in t])
    cmap = np.stack([cents_mapping[center[i] - 4:center[i] + 5] for i in t])
    dev = (win * cmap).sum(1) / win.sum(1)
    dev[salience.max(1) <= thred] = 0
    f0 = 10 * (2 ** (dev / 1200))
    f0[f0 == 10] = 0
    return f0


def vc_chain(model, synth, audio0, pitch, pitchf, sid,
             index, big_npy, index_rate, protect):
    with torch.no_grad():
        feats_t = torch.from_numpy(audio0).float().view(1, -1)
        if isinstance(model, torch.jit.ScriptModule):
            feats = model(feats_t)
        else:
            padding_mask = torch.BoolTensor(feats_t.shape).fill_(False)
            logits = model.extract_features(source=feats_t, padding_mask=padding_mask,
                                            output_layer=12)
            feats = logits[0]
        feats_hu = feats.clone()
        if index is not None and big_npy is not None and index_rate != 0:
            npy = feats[0].cpu().numpy()
            score, ix = index.search(npy, 8)
            weight = np.square(1.0 / score)
            weight /= weight.sum(axis=1, keepdims=True)
            npy2 = np.sum(big_npy[ix] * np.expand_dims(weight, 2), axis=1)
            feats = torch.from_numpy(npy2).unsqueeze(0) * index_rate + (1 - index_rate) * feats
        feats = F.interpolate(feats.permute(0, 2, 1), scale_factor=2).permute(0, 2, 1)
        if protect < 0.5 and pitch is not None:
            feats0 = F.interpolate(feats_hu.permute(0, 2, 1), scale_factor=2).permute(0, 2, 1)
        p_len = audio0.shape[0] // WINDOW
        if feats.shape[1] < p_len:
            p_len = feats.shape[1]
            pitch = pitch[:, :p_len]
            pitchf = pitchf[:, :p_len]
        if protect < 0.5 and pitch is not None:
            pitchff = pitchf.clone()
            pitchff[pitchf > 0] = 1
            pitchff[pitchf < 1] = protect
            pitchff = pitchff.unsqueeze(-1)
            feats = feats * pitchff + feats0 * (1 - pitchff)
        rnd = torch.randn(1, 192, p_len)
        p_len_t = torch.tensor([p_len], dtype=torch.long)
        if isinstance(synth, torch.jit.ScriptModule):
            audio1 = synth(feats, p_len_t, pitch, pitchf, sid, rnd)[0, 0]
        else:
            audio1 = synth(feats, p_len_t, pitch, pitchf, sid, rnd)[0, 0]
        audio1 = audio1.float().numpy()
    return audio1, feats, feats_hu, p_len


def main():
    print("=" * 64)
    print("  VERIFICA_E2E - catena mobile (.ptl+index+rmvpe) vs rvc_python")
    print("=" * 64)

    wav_in = make_test_wav(os.path.join(OUT_DIR, "input.wav"))
    ref_wav = os.path.join(OUT_DIR, "reference_rvc_python.wav")

    from rvc_python.infer import RVCInference
    from rvc_python.lib.audio import load_audio
    from rvc_python.modules.vc.pipeline import Pipeline, bh, ah

    print("[1/5] Reference rvc_python (eager, index, rmvpe)...")
    rvc = RVCInference(models_dir=MODEL_DIR, device="cpu",
                       model_path=PTH, index_path=INDEX)
    rvc.set_params(f0method="rmvpe", index_rate=INDEX_RATE,
                   rms_mix_rate=1.0, protect=PROTECT, filter_radius=3)
    rvc.infer_file(wav_in, ref_wav)
    _, ref16 = wavfile.read(ref_wav)

    print("[2/5] Preprocessing + F0 (stessi passi della pipeline)...")
    pl = Pipeline(rvc.vc.tgt_sr, rvc.config, lib_dir=rvc.lib_dir)
    net_g = rvc.vc.net_g
    hubert = rvc.vc.hubert_model

    audio16 = load_audio(wav_in, SR16)
    audio_max = np.abs(audio16).max() / 0.95
    if audio_max > 1:
        audio16 = audio16 / audio_max
    audio = signal.filtfilt(bh, ah, audio16)
    audio_pad = np.pad(audio, (T_PAD, T_PAD), mode="reflect")
    p_len = audio_pad.shape[0] // WINDOW

    pitch_c, pitchf_hz = pl.get_f0(wav_in, audio_pad, p_len, 0, "rmvpe", 3, None)
    f0_torch_full = pitchf_hz.copy()
    pitch = torch.from_numpy(pitch_c[:p_len].astype(np.int64)).unsqueeze(0)
    pitchf = torch.from_numpy(pitchf_hz[:p_len].astype(np.float32)).unsqueeze(0)
    sid = torch.tensor([0], dtype=torch.long)
    print(f"    audio_pad={audio_pad.shape[0]} p_len={p_len} f0_torch_len={len(f0_torch_full)}")

    print("[3/5] Catena eager (orchestrazione replicata, rnd esplicito)...")
    index = faiss.read_index(INDEX)
    big_npy = index.reconstruct_n(0, index.ntotal).astype(np.float32)
    synth_e = SynthRnd(net_g).eval()
    torch.manual_seed(SEED)
    audio_e, feats_e, feats_hu_e, p_len_e = vc_chain(
        hubert, synth_e, audio_pad, pitch, pitchf, sid,
        index, big_npy, INDEX_RATE, PROTECT)
    audio_e = audio_e[T_PAD_TGT:-T_PAD_TGT]

    print("[4/5] Catena mobile (.ptl + stessa index + stesso f0 + stesso rnd)...")
    hu_ptl = torch.jit.load(HUPTL).eval()
    sy_ptl = torch.jit.load(SYNPTL).eval()
    torch.manual_seed(SEED)
    audio_m, feats_m, feats_hu_m, p_len_m = vc_chain(
        hu_ptl, sy_ptl, audio_pad, pitch, pitchf, sid,
        index, big_npy, INDEX_RATE, PROTECT)
    audio_m = audio_m[T_PAD_TGT:-T_PAD_TGT]

    print("[5/5] rmvpe.onnx (app) vs rmvpe torch (reference)...")
    sess = ort.InferenceSession(RMVPE_ONNX, providers=["CPUExecutionProvider"])
    from rvc_python.lib.rmvpe import MelSpectrogram
    mel_x = MelSpectrogram(False, 128, SR16, 1024, 160, None, 30, 8000)
    mel = mel_x(torch.from_numpy(audio_pad).float().unsqueeze(0), center=True)
    hidden = sess.run(None, {"input": mel.numpy()})[0][0]
    f0_onnx = decode_onnx_f0(hidden)
    n = min(len(f0_onnx), len(f0_torch_full))
    voiced = (f0_torch_full[:n] > 0) & (f0_onnx[:n] > 0)
    d_f0 = np.abs(f0_onnx[:n] - f0_torch_full[:n])
    semis = np.abs(1200 * np.log2(np.maximum(f0_onnx[:n], 1e-9) / np.maximum(f0_torch_full[:n], 1e-9)))
    agree = 100.0 * np.mean(semis[voiced] < 100) if voiced.sum() else float("nan")

    d_phone = float((feats_hu_e - feats_hu_m).abs().max())
    d_phone_blend = float((feats_e - feats_m).abs().max())
    d_audio = float(np.abs(audio_e - audio_m).max())
    d_audio_mean = float(np.abs(audio_e - audio_m).mean())
    e16, m16 = to_int16(audio_e), to_int16(audio_m)
    snr = 10 * np.log10((audio_e ** 2).sum() / ((audio_e - audio_m) ** 2).sum() + 1e-12)
    d_ref = int(np.abs(ref16.astype(np.int64) - m16.astype(np.int64)).max())
    rms_ref = float(np.sqrt((ref16.astype(np.float64) ** 2).mean()))
    rms_mob = float(np.sqrt((m16.astype(np.float64) ** 2).mean()))
    ratio = rms_mob / (rms_ref + 1e-12) if rms_ref > 0 else float("nan")
    len_ok = len(ref16) == len(m16)

    wavfile.write(os.path.join(OUT_DIR, "eager_out.wav"), SR, e16)
    wavfile.write(os.path.join(OUT_DIR, "mobile_out.wav"), SR, m16)

    print()
    print(f"    phone hubert  (eager vs ptl)  max diff = {d_phone:.3e}")
    print(f"    phone blended (eager vs ptl)  max diff = {d_phone_blend:.3e}")
    print(f"    audio  (eager vs ptl, rnd allineato) max diff = {d_audio:.3e}  mean = {d_audio_mean:.3e}  SNR = {snr:.1f} dB")
    print(f"    audio  (mobile vs reference) int16 diff = {d_ref}  (realizzazioni RNG diverse: atteso)")
    print(f"    rms    (mobile vs reference) rapport = {ratio:.3f}  lunghezza = {len(ref16)} vs {len(m16)} {'OK' if len_ok else 'DIVERSA!'}")
    print(f"    f0     (onnx vs torch rmvpe)  max Hz diff = {d_f0.max():.2f}  accordo<100cents = {agree:.1f}%")

    ok = (d_phone < 1e-4 and d_phone_blend < 1e-4 and d_audio < 1e-3
          and len_ok and agree >= 90.0 and 0.85 <= ratio <= 1.18)
    print()
    print("=" * 64)
    print(f"  [{'OK' if ok else 'FAIL'}] VERIFICA E2E COMPLETATA")
    print(f"  File in: {OUT_DIR}")
    print("=" * 64)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()