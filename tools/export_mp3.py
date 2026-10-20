"""
============================================================
  export_mp3.py - Converte un WAV in MP3 usando PyAV (libavcodec
  + libmp3lame). Runtime: K:\\AI\\rvc-portable\\runtime\\python.exe.

  USO:
    Trascina un .wav su ESPORTA_MP3.bat
    oppure
    runtime\\python.exe export_mp3.py <wav_path> [--bitrate 192k] [--out dir]

  Opzioni:
    --bitrate   Bitrate CBR (es. 128k, 192k, 256k, 320k). Default 192k.
    --vbr       Modalita VBR con quality 0-9 (es. --vbr 2). Default CBR.
    --sr        Sample rate di uscita (default: stessa del WAV).
    --ch        Canali: 1 (mono) o 2 (stereo). Default: stessa del WAV.
============================================================
"""
import os
import sys
import argparse
import tkinter as tk
from tkinter import filedialog

import av
import numpy as np


def log(msg):
    print(f"[wav->mp3] {msg}", flush=True)


def get_input_file(arg):
    if arg and os.path.isfile(arg):
        return arg
    root = tk.Tk()
    root.withdraw()
    path = filedialog.askopenfilename(
        title="Seleziona il file WAV da convertire in MP3",
        filetypes=[("WAV", "*.wav"), ("All files", "*.*")],
    )
    root.destroy()
    return path or None


def parse_bitrate(s):
    s = s.strip().lower().replace("kbps", "k").replace("kbs", "k")
    if s.endswith("k"):
        return int(float(s[:-1]) * 1000)
    if s.endswith("m"):
        return int(float(s[:-1]) * 1_000_000)
    return int(s)


def encode_wav_to_mp3(wav_path, out_path, bitrate_bps=None, vbr_quality=None,
                     target_sr=None, target_ch=None):
    """Legge il WAV con PyAV, ricodifica in MP3, scrive su out_path."""
    in_container = av.open(wav_path, mode="r")
    try:
        in_stream = in_container.streams.audio[0]
        in_sr = in_stream.codec_context.sample_rate
        in_ch = in_stream.codec_context.channels
        in_format = in_stream.format.name
        log(f"input: {in_sr} Hz, {in_ch} ch, format={in_format}")

        out_sr = target_sr or in_sr
        out_ch = target_ch or in_ch
        if out_ch not in (1, 2):
            out_ch = 2

        out_container = av.open(out_path, mode="w")
        try:
            mp3_stream = out_container.add_stream("libmp3lame", rate=out_sr)
            mp3_stream.channels = out_ch
            mp3_stream.sample_rate = out_sr
            if vbr_quality is not None:
                # PyAV espone 'global_quality' come 0..100 dove 0=best, 100=worst.
                # LAME VBR quality 0..9 -> 0=best, 9=worst. Mappa in global_quality.
                q = max(0, min(9, int(vbr_quality)))
                mp3_stream.options = {"q": str(q), "compression_ratio": "0.0"}
            else:
                mp3_stream.bit_rate = bitrate_bps
            log(f"output: {out_sr} Hz, {out_ch} ch, "
                f"{'VBR q='+str(vbr_quality) if vbr_quality is not None else 'CBR '+str(bitrate_bps)+' bps'}")

            resampler = av.AudioResampler(format="s16", layout=1 if out_ch == 1 else 2, rate=out_sr)

            total_in = 0
            total_out = 0
            for frame in in_container.decode(in_stream):
                total_in += frame.samples
                resampled = resampler.resample(frame)
                if not isinstance(resampled, list):
                    resampled = [resampled]
                for rf in resampled:
                    for pkt in mp3_stream.encode(rf):
                        out_container.mux(pkt)
                        total_out += 1
            # Flush
            for rf in resampler.resample(None):
                for pkt in mp3_stream.encode(rf):
                    out_container.mux(pkt)
                    total_out += 1
            for pkt in mp3_stream.encode(None):
                out_container.mux(pkt)
                total_out += 1

            duration = total_in / float(out_sr) if out_sr else 0
            size_mb = os.path.getsize(out_path) / 1e6
            log(f"  OK: {total_in} campioni in, {duration:.2f} s, file {size_mb:.2f} MB")
        finally:
            out_container.close()
    finally:
        in_container.close()


def main():
    parser = argparse.ArgumentParser(description="Esporta un WAV in MP3 (PyAV + libmp3lame).")
    parser.add_argument("wav", nargs="?", help="File WAV di input (opzionale, apre dialog)")
    parser.add_argument("--out", help="Directory di output (default: stessa del WAV)")
    parser.add_argument("--bitrate", default="192k", help="Bitrate CBR, es. 128k/192k/320k (default 192k)")
    parser.add_argument("--vbr", type=int, choices=range(0, 10), metavar="Q", help="VBR quality 0-9 (sovrascrive --bitrate)")
    parser.add_argument("--sr", type=int, help="Sample rate di uscita (default: stessa del WAV)")
    parser.add_argument("--ch", type=int, choices=[1, 2], help="Canali di uscita (default: stessa del WAV)")
    args = parser.parse_args()

    wav_path = get_input_file(args.wav)
    if not wav_path:
        log("Nessun file selezionato, esco.")
        return 1

    out_dir = args.out or os.path.dirname(os.path.abspath(wav_path))
    os.makedirs(out_dir, exist_ok=True)
    base = os.path.splitext(os.path.basename(wav_path))[0]
    out_path = os.path.join(out_dir, base + ".mp3")

    bitrate_bps = parse_bitrate(args.bitrate) if args.vbr is None else None

    try:
        encode_wav_to_mp3(
            wav_path,
            out_path,
            bitrate_bps=bitrate_bps,
            vbr_quality=args.vbr,
            target_sr=args.sr,
            target_ch=args.ch,
        )
    except Exception as e:
        import traceback
        traceback.print_exc()
        log(f"ERRORE: {e}")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
