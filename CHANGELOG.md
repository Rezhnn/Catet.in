# Changelog

Semua perubahan penting didokumentasikan di sini. Format mengikuti Keep a Changelog dan versi mengikuti Semantic Versioning.

## [0.1.1] - 2026-11-17

### Fixed

- Memperbaiki force close berulang di Beranda setelah beberapa anggaran dan transaksi dibuat. ID dari tabel berbeda kini memakai key daftar bernamespace sehingga data lama tidak perlu dihapus.
- Kegagalan penyimpanan transaksi atau rencana sekarang menampilkan pesan dan tidak menjatuhkan aplikasi.
- Form transaksi baru selalu mulai bersih; detail transaksi tidak lagi berpotensi menampilkan atau menghapus transaksi lain setelah berpindah layar.
- Penyimpanan transaksi manual dan audit sumbernya kini atomik, dan klik ganda tidak membuat catatan ganda.
- Tautan "Kelola" pada ringkasan Beranda sekarang membuka tab Anggaran; sebelumnya membuka Pengaturan.

### Changed

- Target dipindahkan dari dialog anggaran ke tab **Target Capaian** tersendiri. Target diukur dari sisa uang bulan berjalan (pemasukan dikurangi pengeluaran).
- Tab Anggaran kini menjelaskan perbedaan **Batas** (pagu maksimum) dan **Estimasi** (perkiraan belanja, bukan batas keras).
- Batas, estimasi, dan target dihitung terpisah supaya ringkasan dan progres tidak saling tercampur.
- Rencana dapat diubah dengan mengetuk kartu, dan penghapusan memakai konfirmasi.
- Tombol Kembali mempertahankan layar induk (misalnya Detail → Ubah → Kembali kembali ke Detail).

### Added

- Tombol eksplisit **Unduh model AI** di tab Asisten, lengkap dengan ukuran, persetujuan, progres, status terpasang, dan aksi coba lagi.

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
