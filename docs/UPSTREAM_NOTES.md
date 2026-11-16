# Catatan upstream dan lisensi

## Hubungan dengan PennyWise

PRD Catet.in mempelajari fitur publik dan arsitektur [PennyWise AI](https://github.com/sarim2000/pennywiseai-tracker), proyek AGPL-3.0. Catet.in adalah implementasi Android independen untuk kebutuhan pribadi, bukan source fork dan tidak membawa aset, merek, layanan, atau modul PennyWise.

Karena brief produk berasal dari proyek AGPL dan repository ini dapat berisi komponen turunan, Catet.in dirilis di bawah AGPL-3.0. Atribusi ada di `NOTICE`.

## Keputusan teknis

- Baseline Room baru versi 1; tidak ada migrasi SMS-era dari upstream.
- SMS, izin SMS, receiver SMS, dan scanner SMS tidak disalin.
- Parser Indonesia ditulis baru di `parser-core`; fixture sintetis dan unit test disertakan di repository.
- AI memakai llama.cpp/GGUF melalui `llama-android`, bukan LiteRT-LM atau model PennyWise.
- UI, nama paket, ikon, dan resource memakai identitas Catet.in.
- Tidak ada Play Billing, lisensi Pro, in-app update/review, Crowdin, atau layanan pihak ketiga milik upstream.

## Lisensi pihak ketiga

| Komponen | Lisensi |
| --- | --- |
| Catet.in | AGPL-3.0 |
| PennyWise (proyek referensi) | AGPL-3.0 |
| Qwen3.5-0.8B | Apache-2.0 |
| llama-android | MIT |
| llama.cpp | MIT |
| AndroidX/Jetpack, Hilt, AppAuth, Kotlin, Gradle | Sesuai metadata artefak masing-masing |

Teks lengkap lisensi Catet.in ada di `LICENSE`; atribusi proyek dan model ada di `NOTICE`. Jangan menambahkan aset bermerek PennyWise atau Qwen tanpa izin pemilik merek.
