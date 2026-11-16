# Menambah atau memperbaiki parser

Parser berada di `parser-core` dan wajib tetap murni Kotlin/JVM tanpa import Android.

## Prinsip

1. **Jangan menebak nominal atau arah.** String yang ambigu menghasilkan `Unparsed`/review, bukan transaksi dengan keyakinan palsu.
2. **Data sintetis saja.** Jangan commit nomor rekening, nama orang, referensi, atau email nyata. Ubah angka dengan pola dan magnitudo serupa; pertahankan struktur kalimat/pemisah.
3. **Allow-list dulu.** Sampel notifikasi hanya boleh berasal dari paket yang sudah diizinkan pengguna.
4. **Promosi bukan transaksi.** OTP, login, verifikasi, promosi, pembayaran gagal/pending, dan pesan saldo saja harus `Ignored`.
5. **Tanggal tidak dikarang.** `occurredAt` berasal dari waktu artefak; bila teks punya timestamp eksplisit dan format terbukti, parser dapat memakainya.

## Langkah TDD

1. Tambahkan fixture minimal di `parser-core/src/test` untuk transaksi keluar, masuk, transfer, refund, fee, dan negatif non-transaksi yang relevan.
2. Tulis/perluas tes yang gagal dengan assertion nominal, mata uang, arah, institusi, merchant, dan referensi.
3. Jika format institusi khusus, buat parser di `InstitutionParsers.kt` atau base class turunan yang jelas.
4. Daftarkan institusi di `InstitutionRegistry` dan `ParseEngine`; tambahkan package/sender hint hanya jika sudah diamati.
5. Jalankan `./gradlew :parser-core:test :app:testDebugUnitTest`.
6. Tambahkan kasus dedupe lintas notifikasi/email bila format merchant berbeda secara wajar.

## Format output

`ParsedTransaction` memuat nominal `BigDecimal`, currency ISO, direction `EXPENSE|INCOME|TRANSFER`, institusi, merchant opsional, kategori, account hint 4 digit, reference opsional, timestamp, narration, dan confidence. Jangan menyimpan angka rekening penuh.
