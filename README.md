# Kotoba — Benkyou Shimasu

Dua versi untuk latihan listening percakapan Jepang: **web lokal di laptop** dan
**aplikasi Android lokal**, dalam satu repository. Jepang, romaji, dan terjemahan
Indonesia mengikuti bagian audio yang sedang diputar, termasuk saat seek.

## Web lokal

Gunakan Node.js 22.19+ dan jalankan:

```sh
npm ci
npm start
```

Buka http://127.0.0.1:3210. Microsoft Edge online dapat digunakan tanpa model lokal.
VOICEVOX memakai runtime lokal Python dan model resmi. Folder runtime, hasil audio,
dan dependency lokal tidak dimasukkan ke Git. Rincian versi laptop ada di `README.txt`.

## Android Lite dan Full

Dua APK tersedia langsung di [GitHub Releases](https://github.com/faisal-amiruddin/kotoba-benkyou-shimasu/releases/latest):

| Versi | Provider | Unduh langsung |
| --- | --- | --- |
| Lite | Microsoft online: Nanami dan Keita; tanpa model/kamus/library native VOICEVOX | [kotoba-lite.apk](https://github.com/faisal-amiruddin/kotoba-benkyou-shimasu/releases/latest/download/kotoba-lite.apk) |
| Full | Microsoft online + VOICEVOX offline | [kotoba-full.apk](https://github.com/faisal-amiruddin/kotoba-benkyou-shimasu/releases/latest/download/kotoba-full.apk) |

Background playback, kontrol notifikasi, layar mati, dan subtitle sinkron tersedia
pada keduanya. Lite memakai ID `id.kotoba.studio.lite`; Full tetap memakai
`id.kotoba.studio` sehingga dapat memperbarui versi lama. Keduanya bisa dipasang
bersamaan, dengan data terpisah. Lite langsung membuka UI tanpa menyiapkan model.

Web dan kedua aplikasi langsung membuka kartu Prompt generator tanpa header/hero
dan alur tiga langkah. Pilih **Tema → Ikuti perangkat / Terang / Gelap** di kartu
tersebut. Preferensi tersimpan dan tema Android juga menyesuaikan system bar serta
layar penyiapan. Ikon adaptive berasal dari `assets/branding/kotoba-icon-foreground.png`;
prompt imagegen disimpan di `assets/branding/icon-prompt.txt`.

Full adalah aplikasi Android 8.0+ untuk **ARM64** (termasuk target Tecno Camon 40 Pro 5G),
dengan ABI x86_64 untuk pengujian emulator. VOICEVOX CORE, ONNX Runtime CPU,
kamus Jepang, dan semua paket suara yang dipilih dibundel dalam APK.
Aplikasi tidak memerlukan laptop/server. Pilih **VOICEVOX offline** atau
**Microsoft Edge online** (Nanami dan Keita langsung dari HP). Izin internet
digunakan Microsoft online saat preview/generate; VOICEVOX tetap diproses di HP.

- UI dan parser dialog memakai file web yang sama dengan versi laptop.
- Pada Full, pertama kali dibuka model disiapkan ke penyimpanan internal. Sediakan sekitar
  1,5 GB ruang kosong untuk APK dan model; APK berukuran besar karena berisi model offline.
- Pilih suara cowok/cewek dan gayanya, lalu Preview atau Generate.
- Microsoft online mengirim kalimat Jepang ke layanan Microsoft melalui HTTPS/WSS.
  Romaji dan terjemahan tidak dibacakan. Setelah audio dibuat, pemutaran tidak
  memerlukan internet, termasuk di background.
- Download WAV dan kredit suara membuka pemilih lokasi penyimpanan Android.
- Pemutaran memakai layanan media native: audio tetap berjalan saat membuka aplikasi
  lain atau mengunci layar, dengan play/pause dan seek di kontrol media Android.
  Subtitle mengikuti posisi pemutar saat kembali ke aplikasi. Audio terakhir disimpan
  di penyimpanan aplikasi dan dapat dilanjutkan ketika antarmuka dibuka lagi.
- Untuk generate dialog panjang, tetap buka aplikasi di depan. Pemrosesan dapat
  memakai baterai dan memerlukan waktu; performa HP fisik harus diuji terpisah.
- Microsoft Narrator offline khusus Windows tidak dibundel dalam aplikasi Android.

### Build di GitHub Actions

Unduh APK langsung dari [GitHub Releases](https://github.com/faisal-amiruddin/kotoba-benkyou-shimasu/releases/latest).
Pilih file **kotoba-lite.apk** atau **kotoba-full.apk** di Assets; tidak perlu ZIP.

Workflow **Build Android APK** berjalan saat push ke `main` atau lewat
Actions → Run workflow. Workflow mengunduh aset resmi dengan SHA-256 yang dipin,
menjalankan tes web/pelafalan, membangun APK, dan mengetes sintesis serta alur WebView
pada emulator Android x86_64, termasuk pemutaran saat aplikasi di latar belakang dan
layar mati, serta sintesis Microsoft online Nanami/Keita yang sesungguhnya.
Artifact **kotoba-android-apks** memuat kedua APK untuk build biasa. CI memeriksa
bahwa Lite tidak berisi `.vvm`, kamus, JNI VOICEVOX, atau library native dan
menjalankan tes background/layar mati serta sintesis online pada kedua aplikasi.
Untuk menerbitkan APK langsung, jalankan workflow manual dengan `release_tag`
sesuai `versionName` di `android/common-android.gradle` (misalnya `v1.2.0`) dan siapkan catatan di `release-notes/`.
Publikasi hanya berjalan setelah semua tes lolos, dengan APK dan checksum SHA-256.

APK ini build debug untuk pemasangan pribadi. Kunci debug disimpan lewat cache CI;
untuk distribusi produksi gunakan signing key rilis sendiri. Pengujian emulator
tidak menggantikan pengujian di Tecno fisik.

Build manual membutuhkan JDK 17, Gradle 8.10.2, Android SDK 35, NDK 27.2.12479018,
dan CMake 3.22.1:

```sh
python scripts/prepare_android.py
gradle -p android :app:assembleDebug :lite:assembleDebug
```

## Pelafalan

Mode **Alami** menjadi default VOICEVOX pada kedua versi:

- Tidak menghapus vokal panjang (`ii`, `oo`, `aa`, `ee`) atau nasal `ん` diikuti `n`.
- Mempertahankan jumlah mora, fonem, dan pitch/accent yang diprediksi model.
- Membatasi durasi bunyi panjang/nasal yang berlebihan.
- Mematikan kenaikan pertanyaan tambahan yang dapat memanjangkan akhir `か`.
- Mode **Bawaan model** tersedia untuk membandingkan hasil asli.

Ini penyesuaian prosodi, bukan penggantian model atau jaminan pelafalan sempurna.
Romaji/terjemahan tetap hanya ditampilkan; teks Jepang saja yang disintesis.

## Kredit dan ketentuan suara

Audio VOICEVOX menampilkan kredit karakter dan menyediakan file kredit untuk diunduh.
Kredit Mochiko menyertakan pengisi suara 明日葉よもぎ. Aset build menyertakan ketentuan
VOICEVOX, masing-masing karakter, kamus, ONNX Runtime, dan library native.
Ketentuan pemakaian setiap karakter tetap berlaku, termasuk untuk publikasi/komersial.

Sumber resmi: [VOICEVOX CORE](https://github.com/VOICEVOX/voicevox_core),
[model VVM](https://github.com/VOICEVOX/voicevox_vvm),
[ketentuan VOICEVOX](https://voicevox.hiroshiba.jp/term/).

## Verifikasi

```sh
npm test
python -m unittest discover -s tests -p '*_test.py'
```

Tes engine laptop dilewati jika runtime lokal tidak tersedia. Workflow Android
menjalankan pengujian native dan WebView dengan model yang sesungguhnya.
