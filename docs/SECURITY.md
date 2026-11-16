# Model keamanan dan privasi

## Ringkasan

Catet.in mengurangi permukaan data dengan tidak memiliki backend, iklan, analytics, crash reporting, akses SMS, lokasi, kontak, atau layanan aksesibilitas. Data transaksi berada di database Room privat aplikasi.

## Aset yang dilindungi

- Isi notifikasi dari aplikasi keuangan.
- Isi/subjek/header email transaksi.
- Access token dan refresh token Gmail.
- Ledger transaksi, saldo, anggaran, aturan, dan backup.
- Berkas model GGUF dan hash integritasnya.

## Batas kepercayaan

| Komponen | Kepercayaan |
| --- | --- |
| Notifikasi OS | Isi dianggap data tidak tepercaya; hanya paket allow-list yang dibaca. |
| Email | Semua header/body dianggap data tidak tepercaya dan tidak dieksekusi. |
| Model AI | Output tidak tepercaya sampai lolos parser JSON dan daftar intent tertutup. |
| Gmail API | Kanal TLS ke endpoint Google; hanya respons schema yang diharapkan. |
| Hugging Face | Unduhan HTTPS; isi diverifikasi SHA-256 dan magic GGUF. Model impor pengguna tidak dipatok hash resmi. |
| Room/SharedPreferences | Dilindungi sandbox Android dan app lock; tidak dienkripsi penuh. |

## Notifikasi

`onNotificationPosted` tidak membaca extras. Objek notifikasi dimasukkan ke channel; worker IO memeriksa `packageName` terhadap allow-list database. Hanya setelah lolos, title/text/bigText/textLines dibaca. Paket lain dibuang tanpa pencatatan teks.

Allowlist awal adalah hipotesis untuk pengujian, bukan jaminan format semua bank. Perubahan wording harus ditangani lewat fixture parser dan sample sanitasi.

## Gmail

- Scope hanya `https://www.googleapis.com/auth/gmail.readonly`.
- OAuth memakai PKCE, state acak, dan `access_type=offline`.
- `AuthState` dienkripsi AES/GCM/NoPadding dengan kunci 256-bit non-exportable di Android Keystore. IV acak disimpan bersama ciphertext.
- Token tidak masuk backup, CSV, log, atau UI.
- Authorization header tidak dicetak. Pesan error dipotong; pola Bearer/`ya29` disaring dari detail HTTP.
- Query hanya untuk pengirim yang aktif dan periode backfill; aplikasi tidak mengunduh seluruh mailbox.
- Putuskan Gmail menghapus token lokal. Pencabutan akses penuh dapat dilakukan dari halaman izin akun Google.

## Jaringan

`android:usesCleartextTraffic="false"` dan `network_security_config.xml` menolak HTTP polos. Domains yang dicantumkan adalah dokumentasi endpoint; network security config Android tidak membatasi domain secara efektif sebagai firewall. Kode aplikasi hanya membuka `gmail.googleapis.com`, `oauth2.googleapis.com`, `accounts.google.com`, dan URL model Hugging Face yang dipatok.

Tidak ada endpoint Catet.in dan tidak ada telemetry. Fitur yang membuka browser hanya membuka tautan model/izin Google secara eksplisit.

## Penyimpanan dan backup

- Android cloud backup dan device transfer dimatikan untuk database, SharedPreferences, dan file aplikasi.
- Backup JSON dibuat manual oleh pengguna. Teks mentah sumber hanya ikut bila pengguna mengaktifkan opsi tersebut.
- Backup tidak memuat token, client secret, hash biometric, atau kunci Keystore.
- Retensi teks mentah default 90 hari dan dapat diubah. Worker pembersihan hanya menghapus `raw_text`, bukan transaksi yang sudah dikonfirmasi.
- App lock opsional memakai biometrik kuat atau kredensial perangkat. `FLAG_SECURE` opsional mencegah screenshot/app-switcher preview.

Backup JSON tidak dienkripsi oleh aplikasi. Simpan di penyimpanan terenkripsi dan lindungi dengan akses perangkat.

## Izin Android

- `INTERNET`: Gmail OAuth/API dan unduhan model.
- `POST_NOTIFICATIONS`: notifikasi status aplikasi bila diperlukan.
- `USE_BIOMETRIC`: app lock.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: hanya untuk membantu listener/background sync pada OEM yang agresif; aplikasi tetap dapat berjalan tanpa izin ini.
- `BIND_NOTIFICATION_LISTENER_SERVICE`: dipakai sistem untuk service listener; bukan izin SMS.

CI memeriksa manifest sumber agar `READ_SMS`, `RECEIVE_SMS`, lokasi, kontak, `QUERY_ALL_PACKAGES`, dan accessibility service tidak masuk.

## Model AI dan prompt injection

Teks pengguna, notifikasi, email, dan nama merchant diperlakukan sebagai data. Prompt sistem asisten meminta JSON intent saja dan melarang tool/aksi. Output model divalidasi terhadap enum metric, period, kategori yang ada, dan limit. Model tidak memiliki akses database atau fungsi hapus/ubah.

Model berjalan di proses `:ai` terpisah dan dilepas saat app background atau memori rendah. Model resmi diverifikasi hash; model impor diperiksa magic GGUF tetapi dianggap kurang tepercaya karena berasal dari pengguna.

## Batasan yang diketahui

- Perangkat root/compromised atau malware dengan akses ke sandbox aplikasi dapat membaca database.
- Room tidak memakai enkripsi tingkat database; app lock adalah kontrol UI, bukan enkripsi.
- PIN perangkat diperlukan sebagai fallback bila biometrik tidak tersedia.
- APK debug tidak boleh dianggap sebagai rilis keamanan: build debug dapat memuat tooling dan tidak di-minify.
- Kode dan model belum melalui audit keamanan independen.

Jika menemukan masalah, buka private security advisory pada repository GitHub atau hubungi pemilik repository tanpa menempelkan token/email nyata ke issue publik.
