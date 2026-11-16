# Changelog

Semua perubahan penting didokumentasikan di sini. Format mengikuti Keep a Changelog dan versi mengikuti Semantic Versioning.

## [0.1.0] - 2026-11-16

### Added

- Pencatatan transaksi manual, kategori, anggaran, analitik, widget, dan pencarian.
- Ingestion notifikasi dengan allow-list yang diperiksa sebelum teks dibaca.
- Ingestion Gmail read-only dengan OAuth PKCE, token terenkripsi, backfill bertahap, dan sinkronisasi berkala.
- Parser deterministik Indonesia untuk BCA, Mandiri, BRI, BNI, SeaBank, Jago, Jenius, GoPay, OVO, DANA, ShopeePay, LinkAja, Tokopedia, Grab, dan teks generik.
- Dedupe notifikasi/email, Review Queue, audit sumber, dan Unparsed bucket.
- Aturan otomatis, deteksi langganan, backup/restore JSON, ekspor CSV, dan retensi teks mentah.
- App lock biometrik/PIN dan opsi secure window.
- Asisten lokal opsional dengan Qwen3.5-0.8B, lifecycle `:ai`, verifikasi model, dan fallback deterministik.
- Kit fine-tuning QLoRA, dataset sintetis, evaluasi, dan ekspor GGUF.
- CI, dokumentasi setup Gmail, model keamanan, arsitektur, dan alur rilis.

### Known limitations

- Alur OAuth Gmail live dan runtime Qwen3.5 belum diuji pada perangkat fisik dalam build ini.
- Pelatihan adapter belum dijalankan; hanya model dasar yang dipakai secara opsional.
- Perilaku NotificationListener pada OEM agresif dan profil RAM 4 GB masih perlu validasi perangkat.
