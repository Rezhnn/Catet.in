# Menyiapkan Gmail read-only

Catet.in memakai OAuth native Android dan hanya meminta scope:

```text
https://www.googleapis.com/auth/gmail.readonly
```

Scope ini tergolong **restricted**. Untuk pemakaian pribadi, Google mengizinkan aplikasi belum terverifikasi dengan layar persetujuan yang harus diklik. Distribusi publik tetap memerlukan verifikasi Google dan penilaian keamanan pihak ketiga tahunan. Jangan menganggap konfigurasi di bawah sebagai izin distribusi publik otomatis.

## 1. Siapkan Google Cloud

1. Buat atau pilih project di [Google Cloud Console](https://console.cloud.google.com/).
2. Buka **APIs & Services → Library**, cari **Gmail API**, lalu aktifkan.
3. Buka **Google Auth Platform / OAuth consent screen**.
4. Isi nama aplikasi dan email dukungan. Pilih audience **External** untuk akun pribadi.
5. Tambahkan `https://www.googleapis.com/auth/gmail.readonly` ke scopes.
6. Selama pengujian, tambahkan akun Google yang akan dipakai sebagai test user.
7. Setelah alur berhasil, pertimbangkan status **In production (unverified)**. Aplikasi berstatus Testing mengeluarkan refresh token yang kedaluwarsa sekitar 7 hari untuk banyak konfigurasi OAuth.

## 2. Buat OAuth client Android

Buka **APIs & Services → Credentials → Create credentials → OAuth client ID → Android**.

Isi package name sesuai build yang dipasang:

| Build | applicationId |
| --- | --- |
| Debug | `com.catetin.app.debug` |
| Release | `com.catetin.app` |

Tambahkan SHA-1 sertifikat penandatangan yang benar-benar dipakai untuk memasang aplikasi.

Untuk release keystore lokal (jangan menampilkan password di terminal bersama):

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -list -v `
  -keystore .\catetin-release.jks `
  -alias catetin-key `
  -storepass (Get-Content .\keystore_password.txt -Raw).Trim() `
  | Select-String 'SHA1|SHA-1'
```

Untuk debug keystore standar:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -list -v `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -alias androiddebugkey -storepass android -keypass android `
  | Select-String 'SHA1|SHA-1'
```

Gunakan keystore yang akan dipakai jangka panjang. APK release tidak dapat dipasang sebagai pembaruan bila sertifikatnya berubah.

## 3. Berikan client ID ke build

Tambahkan ke `local.properties` (sudah di-ignore):

```properties
GMAIL_OAUTH_CLIENT_ID=1234567890-abcdef.apps.googleusercontent.com
```

Alternatifnya gunakan environment variable dengan nama yang sama. Jangan memakai client secret: aplikasi Android OAuth tidak membutuhkannya, dan file `client_secret*.json` di-ignore.

Redirect scheme dihitung dari client ID. Jika ID berakhir `.apps.googleusercontent.com`, scheme menjadi `com.googleusercontent.apps.<prefix>` dan AndroidManifest menerima `com.googleusercontent.apps.<prefix>:/oauth2redirect`.

## 4. Build, pasang, dan hubungkan

```powershell
.\gradlew.bat :app:assembleRelease --console=plain
```

Pasang APK release, buka **Sumber → Gmail → Hubungkan Gmail**, lalu setujui scope baca saja. Setelah kembali, tekan **Sinkronkan**. Backfill default 30 hari dan dapat diubah di pengaturan.

Periksa status:

- `SYNCING`: backfill/incremental sedang berjalan.
- `OK`: sinkronisasi berhasil.
- `ERROR`: pesan spesifik ditampilkan pada layar Sumber.
- `RECONNECT_NEEDED`: token dicabut/kedaluwarsa; hubungkan ulang.

## 5. Troubleshooting

**`redirect_uri_mismatch` atau izin langsung ditolak**

- Pastikan package name persis, termasuk `.debug` untuk build debug.
- Pastikan SHA-1 berasal dari keystore yang menandatangani APK terpasang.
- Hapus instalasi lama jika berpindah sertifikat, lalu pasang build yang sesuai.

**Refresh token hilang setelah sekitar 7 hari**

- Aplikasi tetap di status Testing. Pindahkan consent screen ke In production (unverified), cabut akses Catet.in di akun Google, lalu hubungkan ulang.

**Gmail terhubung tetapi tidak ada transaksi**

- Pastikan minimal satu pengirim di layar Sumber aktif.
- Coba pengirim/domain yang memang mengirim bukti transaksi, bukan alamat promo.
- Periksa bucket Unparsed dan Review Queue.
- Query Gmail hanya mengambil pengirim aktif dan periode backfill, bukan seluruh kotak masuk.

**`invalid_grant`**

- Token sudah dicabut, password akun berubah, atau consent/TEST user berubah. Pilih putuskan Gmail lalu hubungkan ulang.

## Referensi Google

- [Manage OAuth clients](https://support.google.com/cloud/answer/15549257)
- [Manage app audience and publishing status](https://support.google.com/cloud/answer/15549945)
- [Gmail API scopes](https://developers.google.com/workspace/gmail/api/auth/scopes)
- [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy)

Konfigurasi dan alur OAuth live tetap perlu diuji pada perangkat target; build sumber ini tidak menyertakan client ID atau kredensial OAuth.
