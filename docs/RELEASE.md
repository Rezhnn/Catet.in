# Membuat paket rilis

## Prasyarat

- JDK 21 dan Android SDK yang dapat membangun `:app`.
- Keystore release jangka panjang. Jangan membuat ulang keystore untuk setiap versi.
- Password disimpan di luar Git. Repository mengabaikan `keystore_password.txt`, `*.jks`, `keystore.properties`, dan `*.password`.

## Konfigurasi lokal

Build mendukung environment variable berikut:

```text
CATETIN_KEYSTORE_PATH      path absolut ke file .jks
CATETIN_KEYSTORE_PASSWORD  password keystore
CATETIN_KEY_ALIAS          alias kunci, misalnya catetin-key
CATETIN_KEY_PASSWORD       password kunci (boleh sama dengan keystore)
```

Alternatifnya, isi `keystore.properties` lokal (di-ignore) dengan key yang sama. Jangan pernah menaruh password di `gradle.properties` yang dapat ter-commit.

Contoh PowerShell yang membaca password tersembunyi tanpa mencetaknya:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot'
$env:ANDROID_HOME = 'F:\Android\Sdk'
$env:CATETIN_KEYSTORE_PATH = (Resolve-Path '.\catetin-release.jks').Path
$env:CATETIN_KEYSTORE_PASSWORD = (Get-Content '.\keystore_password.txt' -Raw).Trim()
$env:CATETIN_KEY_ALIAS = 'catetin-key'
$env:CATETIN_KEY_PASSWORD = $env:CATETIN_KEYSTORE_PASSWORD
.\.\gradlew.bat :parser-core:test :app:testDebugUnitTest :app:assembleRelease --console=plain
```

APK berada di `app/build/outputs/apk/release/app-release.apk`. Verifikasi sertifikat:

```powershell
& "$env:JAVA_HOME\bin\apksigner.bat" verify --verbose --print-certs `
  'app\build\outputs\apk\release\app-release.apk'
```

## OAuth untuk build release

Client Android OAuth harus memakai package `com.catetin.app` dan SHA-1 sertifikat release, bukan sertifikat debug. Mengganti keystore setelah pengguna memasang APK akan mencegah update in-place. Detail ada di [GMAIL_SETUP.md](GMAIL_SETUP.md).

## GitHub Release

`scripts/release-debug.ps1` membuat artefak debug-signed untuk pengujian internal. Untuk rilis publik, gunakan APK yang sudah diverifikasi `apksigner`, buat checksum SHA-256, tulis catatan perubahan dari `CHANGELOG.md`, lalu unggah APK dan checksum ke GitHub Releases. Jangan unggah file `.jks`, `keystore_password.txt`, atau `local.properties`.

CI build debug dan test tidak membutuhkan signing secret. Workflow rilis hanya boleh menerima secret melalui GitHub Actions Secrets; secret tidak boleh ditulis ke log atau artefak.
