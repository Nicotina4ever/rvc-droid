"""
============================================================
  export_faiss_bin.py - Esporta un index IVF-Flat RVC in un
  file binario compatto (.fbin) per l'app Android.
  Il C++ replichera' ESATTAMENTE la ricerca faiss:
    - assegnazione al centroide piu' vicino (nprobe liste)
    - top-k per metrica (L2 o IP)
  Validazione: replica in numpy confrontata 1:1 con faiss.

  USO: trascina un file .index sul BAT (o passalo come
  argomento), oppure esegui e scegli il file con l'explorer.
  L'output .fbin viene scritto nella stessa cartella
  dell'index scelto. K:\AI\rvc-portable SOLO IN LETTURA.
============================================================
"""
import os
import re
import sys
import struct
import tkinter as tk
from tkinter import filedialog

PYTHON_DIR = os.path.dirname(sys.executable)
RVC_LIB = os.path.join(PYTHON_DIR, "Lib", "site-packages")
if RVC_LIB not in sys.path:
    sys.path.insert(0, RVC_LIB)

import numpy as np
import faiss

METRIC_INNER_PRODUCT = 0
METRIC_L2 = 1


def get_input_file():
    if len(sys.argv) > 1 and os.path.isfile(sys.argv[1]):
        return sys.argv[1]
    root = tk.Tk()
    root.withdraw()
    path = filedialog.askopenfilename(
        title="Seleziona l'index faiss (.index)",
        filetypes=[("Faiss Index", "*.index"), ("All files", "*.*")]
    )
    root.destroy()
    return path if path else None


def derive_out_name(index_name):
    base = os.path.basename(index_name)
    m = re.match(r"^(?:added_IVF\d+_Flat_nprobe_\d+[A-Za-z_]*_)?(.+?)(?:\.index)?$", base)
    if m and m.group(1):
        return m.group(1) + ".fbin"
    return base.replace(".index", "") + ".fbin"


def faiss_search(index, queries, k):
    return index.search(queries.astype("float32"), k)


def replica_search(centroids, big_npy, lists, metric, nprobe, queries, k):
    n = queries.shape[0]
    scores = np.zeros((n, k), dtype="float32")
    idxs = np.zeros((n, k), dtype="int64")
    for qi in range(n):
        q = queries[qi]
        if metric == METRIC_L2:
            d = np.sum((centroids - q) ** 2, axis=1)
        else:
            d = -np.sum(centroids * q, axis=1)
        order = np.argsort(d, kind="stable")
        cand_ids = []
        cand_d = []
        for li in order[:nprobe]:
            ids = lists[li]
            for j, gid in enumerate(ids):
                v = big_npy[gid]
                if metric == METRIC_L2:
                    dist = float(np.sum((v - q) ** 2))
                else:
                    dist = float(np.sum(v * q))
                cand_ids.append(gid)
                cand_d.append(dist)
        cand_ids = np.array(cand_ids)
        cand_d = np.array(cand_d)
        o = np.argsort(cand_d, kind="stable")
        if metric == METRIC_INNER_PRODUCT:
            o = o[::-1]
        top = o[:k]
        idxs[qi] = cand_ids[top]
        scores[qi] = cand_d[top]
    return scores, idxs


def main():
    print("=" * 60)
    print("  export_faiss_bin.py - IVF index -> .fbin")
    print("=" * 60)

    index_path = get_input_file()
    if not index_path:
        print("  [ERRORE] Nessun file selezionato.")
        return 1
    if not index_path.lower().endswith(".index"):
        print(f"  [ERRORE] Il file non sembra un index faiss: {index_path}")
        return 1

    index = faiss.read_index(index_path)
    d = index.d
    ntotal = index.ntotal
    nlist = index.nlist
    nprobe = index.nprobe
    metric = index.metric_type
    quantizer_metric = index.quantizer.metric_type if hasattr(index.quantizer, "metric_type") else -1
    print(f"  index: {os.path.basename(index_path)}")
    print(f"  d={d} ntotal={ntotal} nlist={nlist} nprobe={nprobe}")
    print(f"  metric_type={metric}  quantizer_metric={quantizer_metric}")
    if d != 768:
        print(f"  [ERRORE] atteso d=768 (feature hubert), trovato d={d}")
        return 1

    big_npy = index.reconstruct_n(0, ntotal).astype("float32")
    centroids = index.quantizer.reconstruct_n(0, nlist).astype("float32")
    print(f"  big_npy {big_npy.shape}  centroids {centroids.shape}")

    lists = {}
    for li in range(nlist):
        ids = faiss.rev_swig_ptr(
            index.invlists.get_ids(li), index.invlists.list_size(li)
        ).copy().astype("int64")
        lists[li] = ids

    rng = np.random.default_rng(42)
    queries = big_npy[rng.integers(0, ntotal, 40)] + rng.normal(0, 0.02, (40, d)).astype("float32")
    s_ref, i_ref = faiss_search(index, queries, 8)
    s_rep, i_rep = replica_search(centroids, big_npy, lists, metric, nprobe, queries, 8)
    same_ids = int((i_ref == i_rep).sum())
    d_scores = float(np.abs(s_ref - s_rep).max())
    print(f"  VALIDAZIONE: ids identici {same_ids}/{i_ref.size}  max diff score {d_scores:.6e}")
    assert same_ids == i_ref.size, "selezione liste/ids NON identica a faiss!"
    assert d_scores < 1e-3, f"score troppo diversi da faiss ({d_scores:.2e})"

    out_name = derive_out_name(os.path.basename(index_path))
    out_path = os.path.join(os.path.dirname(os.path.abspath(index_path)), out_name)
    with open(out_path, "wb") as f:
        f.write(b"RVCX")
        f.write(struct.pack("<i", d))
        f.write(struct.pack("<q", ntotal))
        f.write(struct.pack("<i", nlist))
        f.write(struct.pack("<i", nprobe))
        f.write(struct.pack("<i", metric))
        f.write(struct.pack("<i", 0))  # riservato
        f.write(centroids.astype("<f4").tobytes())
        f.write(big_npy.astype("<f4").tobytes())
        for li in range(nlist):
            ids = lists[li]
            f.write(struct.pack("<q", len(ids)))
            f.write(ids.astype("<q").tobytes())

    size_mb = os.path.getsize(out_path) / 1e6
    print(f"  Scritto: {out_path}  ({size_mb:.1f} MB)")
    print("=" * 60)
    return 0


def export_index(index_path, out_path=None):
    """Entry point riusabile: produce un .fbin (custom: nome/path controllati)."""
    index = faiss.read_index(index_path)
    d = index.d
    ntotal = index.ntotal
    nlist = index.nlist
    nprobe = index.nprobe
    metric = index.metric_type
    print(f"  index: {os.path.basename(index_path)} d={d} ntotal={ntotal} nlist={nlist} nprobe={nprobe} metric={metric}")
    if d != 768:
        print(f"  [ERRORE] atteso d=768 (feature hubert), trovato d={d}")
        raise ValueError(f"index d={d}, atteso 768")

    big_npy = index.reconstruct_n(0, ntotal).astype("float32")
    centroids = index.quantizer.reconstruct_n(0, nlist).astype("float32")

    lists = {}
    for li in range(nlist):
        ids = faiss.rev_swig_ptr(
            index.invlists.get_ids(li), index.invlists.list_size(li)
        ).copy().astype("int64")
        lists[li] = ids

    if out_path is None or os.path.isdir(out_path):
        out_dir = out_path or os.path.dirname(os.path.abspath(index_path))
        out_name = derive_out_name(os.path.basename(index_path))
        out_path = os.path.join(out_dir, out_name)
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)

    with open(out_path, "wb") as f:
        f.write(b"RVCX")
        f.write(struct.pack("<i", d))
        f.write(struct.pack("<q", ntotal))
        f.write(struct.pack("<i", nlist))
        f.write(struct.pack("<i", nprobe))
        f.write(struct.pack("<i", metric))
        f.write(struct.pack("<i", 0))
        f.write(centroids.astype("<f4").tobytes())
        f.write(big_npy.astype("<f4").tobytes())
        for li in range(nlist):
            ids = lists[li]
            f.write(struct.pack("<q", len(ids)))
            f.write(ids.astype("<q").tobytes())
    return out_path


if __name__ == "__main__":
    sys.exit(main())