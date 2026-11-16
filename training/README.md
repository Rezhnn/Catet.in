# Catet.in intent fine-tuning kit

Kit ini melatih LoRA/QLoRA untuk tugas yang sempit: mengubah pertanyaan Bahasa Indonesia menjadi satu objek JSON intent. Model tidak menjawab nominal dan tidak diberi akses ke database. Semua angka tetap dihitung oleh Kotlin deterministik.

## Kebutuhan perangkat

- GPU CUDA dengan VRAM minimal 8 GB; 12 GB lebih nyaman untuk Qwen3.5-0.8B dengan konteks 1.536 token.
- Python 3.10–3.12 dan driver CUDA yang kompatibel dengan PyTorch.
- Ruang disk sekitar 10 GB untuk bobot, checkpoint, adapter, dan ekspor.

Mesin i5-3470 dengan iGPU bukan target pelatihan yang praktis. Kit ini dapat dijalankan di GPU lokal atau layanan cloud; tidak ada klaim bahwa pelatihan sudah dijalankan di perangkat pemilik.

## 1. Siapkan lingkungan

```bash
python -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
```

Dataset sudah disertakan di `data/train.jsonl` dan `data/eval.jsonl`. Untuk membuatnya ulang secara deterministik:

```bash
python generate_dataset.py
```

## 2. Latih adapter

```bash
accelerate launch train_qlora.py \
  --model Qwen/Qwen3.5-0.8B \
  --output output/catetin-qlora
```

Skrip memakai 4-bit NF4, LoRA `r=16`, `alpha=32`, learning rate `2e-4`, tiga epoch, dan cosine scheduler. Template chat diminta tanpa thinking. Bila versi Transformers yang dipakai belum mendukung argumen `enable_thinking`, skrip tetap memakai template resmi dan aplikasi tetap meminta output JSON langsung.

## 3. Evaluasi

```bash
python evaluate.py \
  --adapter output/catetin-qlora \
  --data data/golden_eval.jsonl \
  --output output/eval-report.json
```

Target rilis: validitas JSON ≥ 90% dan akurasi intent ≥ 80%. Nilai aktual harus dicatat di `docs/AI_EVAL.md`; jangan mengisi angka sebelum evaluasi benar-benar dijalankan.

## 4. Merge dan ekspor GGUF

```bash
python merge_adapter.py \
  --adapter output/catetin-qlora \
  --output output/catetin-merged
```

Unduh dan build `llama.cpp`, lalu dari folder tersebut:

```bash
python convert_hf_to_gguf.py /path/ke/output/catetin-merged --outtype f16 --outfile catetin-f16.gguf
./build/bin/llama-quantize catetin-f16.gguf catetin-Q4_K_M.gguf Q4_K_M
```

Impor `catetin-Q4_K_M.gguf` lewat **Pengaturan → Kelola model AI → Impor model GGUF**. Aplikasi akan mencatat SHA-256 berkas impor. GGUF hasil fine-tune tidak harus memiliki hash yang sama dengan model dasar resmi.

## Lisensi dan data

- Model dasar: [Qwen/Qwen3.5-0.8B](https://huggingface.co/Qwen/Qwen3.5-0.8B), Apache-2.0.
- Dataset sintetis di folder ini: CC BY 4.0; tidak berisi data keuangan nyata.
- Adapter/fine-tuned weights mengikuti lisensi model dasar dan ketentuan penyedia komputasi yang dipakai.
- Llama.cpp dan konversi GGUF mengikuti lisensi proyek masing-masing.
