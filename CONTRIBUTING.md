# Berkontribusi

Terima kasih sudah memeriksa Catet.in. Fokus proyek ini adalah pencatatan keuangan lokal yang dapat diaudit, bukan pertumbuhan telemetry atau integrasi cloud.

## Sebelum mengirim perubahan

1. Buat branch kecil dan jelaskan perilaku yang berubah.
2. Jaga semua UI dan pesan pengguna dalam Bahasa Indonesia; format uang memakai `id-ID` dan default IDR.
3. Jangan menambahkan izin SMS, lokasi, kontak, accessibility service, `QUERY_ALL_PACKAGES`, analytics, crash SDK, atau iklan.
4. Jangan mengirim teks notifikasi/email nyata, token, client secret, keystore, atau backup ke repository/issue.
5. Untuk perubahan parser, tambahkan fixture sintetis tersanitasi dan uji regresi untuk transaksi berhasil, gagal, promosi, OTP, refund, transfer, dan valuta asing.
6. Untuk perubahan dedupe, sertakan kasus notifikasi+email, dua pembelian identik, perbedaan merchant, referensi transaksi, dan ambigu.
7. Untuk perubahan AI, pertahankan validasi schema dan larangan tool/aksi. Jangan mengklaim angka evaluasi tanpa report yang dapat direproduksi.

## Pemeriksaan lokal

```bash
./gradlew :parser-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
python -m py_compile training/*.py
```

## Commit dan PR

Gunakan pesan commit yang menjelaskan alasan perubahan. Satu PR sebaiknya memiliki satu tujuan; pisahkan refactor besar dari perubahan perilaku. Jelaskan risiko, batas pengujian, dan apakah alur Gmail/AI/perangkat benar-benar dijalankan.

Dengan mengirim kontribusi, Anda setuju karya tersebut dapat didistribusikan di bawah AGPL-3.0 repository ini.
