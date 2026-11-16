# Dataset

- `train.jsonl` dan `eval.jsonl` memakai format chat `system/user/assistant`.
- `golden_eval.jsonl` menyimpan pertanyaan dan intent yang diharapkan untuk pengukuran akurasi.
- Seluruh contoh dihasilkan dari templat sintetis oleh `generate_dataset.py`; tidak ada transaksi atau identitas nyata.
- Assistant hanya menghasilkan intent JSON. Angka, saldo, dan agregasi tidak pernah menjadi label latihan.
