# Asisten lokal: prompt, dataset, dan evaluasi

## Desain

Model tidak pernah menjadi sumber angka. Tugasnya hanya mengubah pertanyaan Bahasa Indonesia menjadi satu JSON intent:

```json
{"metric":"SPENDING","period":"THIS_MONTH","category":"Makanan & Minuman","limit":0}
```

Kode Kotlin memvalidasi metric/period/kategori, mengambil transaksi Room, menghitung nominal, dan menyusun jawaban. Prompt pengguna diberi instruksi bahwa teks adalah data tidak tepercaya. Thinking diminta nonaktif pada system prompt dan chat template; output yang masih berisi blok pemikiran dibuang sebelum JSON diekstrak.

## Dataset

| Berkas | Jumlah baris | Fungsi |
| --- | ---: | --- |
| `training/data/train.jsonl` | 6.351 | Contoh chat sintetis |
| `training/data/eval.jsonl` | 1.121 | Validasi saat training |
| `training/data/golden_eval.jsonl` | 1.121 | Evaluasi intent dan validitas JSON |

Dataset dibuat deterministik oleh `python training/generate_dataset.py`. Tidak ada transaksi, nama, email, atau nomor akun nyata. Dataset mencakup periode, kategori, pertanyaan tidak didukung, dan variasi prompt injection.

## Menjalankan QLoRA

Persyaratan praktis: CUDA, VRAM minimal 8 GB (12 GB disarankan), Python 3.10–3.12, disk sekitar 10 GB.

```bash
cd training
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
accelerate launch train_qlora.py --model Qwen/Qwen3.5-0.8B --output output/catetin-qlora
```

Konfigurasi awal: NF4 4-bit, LoRA r=16/alpha=32/dropout 0.05, tiga epoch, cosine schedule, gradient checkpointing, batch 1 dengan akumulasi 16. Skrip berhenti dengan pesan jelas bila CUDA tidak tersedia.

## Evaluasi

```bash
python evaluate.py \
  --base Qwen/Qwen3.5-0.8B \
  --adapter output/catetin-qlora \
  --data data/golden_eval.jsonl \
  --output output/eval-report.json
```

Metrik:

- **JSON validity:** proporsi keluaran yang dapat di-parse dan memakai enum yang sah. Target ≥ 90%.
- **Intent accuracy:** kecocokan metric, period, dan category. Target ≥ 80%.

**Belum ada angka aktual.** Pelatihan belum dijalankan pada mesin i5-3470/iGPU ini. `docs/AI_EVAL.md` sengaja tidak memuat klaim kualitas sebelum `output/eval-report.json` dihasilkan. Setelah evaluasi, tambahkan commit report yang telah dibersihkan dari data pribadi.

## Merge dan impor GGUF

```bash
python merge_adapter.py --adapter output/catetin-qlora --output output/catetin-merged
python /path/to/llama.cpp/convert_hf_to_gguf.py output/catetin-merged \
  --outtype f16 --outfile output/catetin-f16.gguf
/path/to/llama.cpp/build/bin/llama-quantize \
  output/catetin-f16.gguf output/catetin-Q4_K_M.gguf Q4_K_M
```

Impor lewat **Pengaturan → Kelola model AI → Impor model GGUF**. Aplikasi mencatat SHA-256 dan memeriksa magic `GGUF`; impor tidak memerlukan token atau kredensial jaringan.

## Model runtime

- Unduhan resmi: `Qwen3.5-0.8B-Q4_0.gguf` dari `ggml-org/Qwen3.5-0.8B-GGUF`.
- Ukuran: 563.036.064 byte.
- SHA-256: `57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf`.
- Lisensi model: Apache-2.0.
- AAR runtime: `dev.ffmpegkit-maintained:llama-android:0.1.1`, hanya `arm64-v8a`.

**Belum diverifikasi pada perangkat:** apakah AAR tersebut memuat dukungan arsitektur/template chat Qwen3.5 secara penuh. Bila pemuatan gagal, aplikasi tetap berfungsi dan menampilkan fallback deterministik.
