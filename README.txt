KOTOBA STUDIO — Microsoft & VOICEVOX
=======================================

MENJALANKAN
-----------
- Node.js LTS; direkomendasikan Node 22.19+ di Windows untuk sertifikat sistem.
- Jalankan start.bat atau npm start; buka http://127.0.0.1:3210.
- Pilih VOICEVOX (offline), Microsoft lokal (offline), atau Microsoft Edge (online).
- Jika VOICEVOX terpasang, VOICEVOX menjadi pilihan awal untuk browser baru.
  Pilihan provider Anda yang sudah disimpan tetap digunakan.
- Mode Microsoft lokal memakai Nanami/Keita Narrator 1.0.5.0 yang dipasang resmi melalui Store.
- Ukuran terpasang: Keita 28.614.803 byte; Nanami 28.626.445 byte; total 57,24 MB.
- Penghubung open source NaturalVoiceSAPIAdapter 0.2.11 ada di
  .runtime/microsoft-offline/adapter. Jangan memindahkan folder tersebut.
- Penghubung ini bukan API resmi Microsoft untuk aplikasi pihak ketiga;
  pembaruan Windows/paket suara dapat memengaruhi kompatibilitasnya.
- Registrasi COM hanya untuk pengguna Windows saat ini, tanpa hak administrator.
- Daftar offline dibaca dari kategori SAPI pengguna; hanya token bertipe Local
  untuk bahasa Jepang yang diizinkan. Edge/Azure pada adapter dinonaktifkan.
- Mode offline tidak mengirim teks ke Edge dan tidak beralih otomatis ke online.
- Mode online tetap memakai msedge-tts@2.1.1 dan memerlukan internet.
- Tidak memerlukan API key Azure untuk mode Microsoft tersebut.

VOICEVOX OFFLINE
---------------
- Menggunakan VOICEVOX CORE Python resmi 0.17.1, model VVM 0.17.1,
  VOICEVOX ONNX Runtime 1.17.3 CPU, dan Open JTalk dictionary 1.11.
- Paket inti dipasang di .runtime/voicevox; paket GUI lengkap tidak diperlukan.
- Runtime Python memakai venv tersendiri, tanpa mengubah Python sistem.
- Worker dimulai otomatis saat diperlukan, lalu digunakan kembali saat generate.
  Tidak perlu menjalankan server VOICEVOX lain. Empat thread CPU dipakai.
- Model 0.vvm: Shikoku Metan, Zundamon, Kasukabe Tsumugi, Amehare Hau.
- Model 4.vvm: Kurono Takehiro dan Kenzaki Mesuo.
- Model 6.vvm: No.7 (Normal, Pengumuman, Bercerita).
- Model 7.vvm dan 16.vvm: Goki (wujud manusia, boneka, manusia marah, oni).
- Model 15.vvm: Ryusei Aoyama dan Mochiko-san beserta gaya emosinya;
  juga menambah gaya Lemas/Berkaca-kaca untuk Zundamon yang sudah terpasang.
- Model 18.vvm: Chubu Tsurugi beserta gaya Marah, Bisik-bisik, Gugup, Putus asa.
- Model 19.vvm: Kurosawa Kohaku (nama sesuai tautan kurosawa_kohaku).
- Maksimal tiga model disimpan aktif di RAM. Model lama dilepas otomatis
  saat berganti ke model lain; seluruh suara tetap tersedia di daftar.
- Gaya bicara adalah pilihan pada daftar suara; default Takehiro Normal dan Metan Normal.
- Tempo per pembicara 0,70–2,00x. Variasi intonasi 0–2, default asli model = 1.
- Tidak ada pitch acak, modifikasi otomatis naskah, atau perpindahan ke provider online.
- Kredit VOICEVOX dan nama karakter ditampilkan setelah generate dan dapat
  diunduh sebagai voice-credits.txt; sertakan kredit saat membagikan audio.
- Kredit Mochiko-san menyertakan pemeran suara: VOICEVOX:もち子さん(cv明日葉よもぎ).
- Ketentuan karakter disimpan di .runtime/voicevox/models/README-and-TERMS.md.
- Ukuran setelah tambahan karakter sekitar 614 MB; termasuk arsip setup sekitar 652 MB.
  Rincian aktual tersimpan di outputs/voicevox-install-size.json.
- Semua bagian berasal dari rilis resmi VOICEVOX; SHA-256 model dan wheel
  diverifikasi terhadap digest aset rilis GitHub.

PROMPT DAN NASKAH
----------------
- Pilih JLPT N5–N1, durasi 1–90 menit, serta tema opsional.
- Salin prompt ke LLM pilihan Anda lalu tempel naskah hasilnya.
- Satu percakapan cowok–cewek; tidak ada pembagian topik otomatis/bel/narator.
- Nomor global terus bertambah untuk kedua pembicara:
  {cowo[1]} こんにちは。
  {romaji[1]} Konnichiwa.
  {translate[1]} Halo.
  {cewe[2]} お元気ですか？
  {romaji[2]} Ogenki desu ka?
  {translate[2]} Apa kabar?
- Hanya Jepang dibacakan. Romaji dan arti berasal dari LLM.
- Jepang/romaji/arti mengikuti audio termasuk seek, pause, dan putar ulang.

TEMPO DAN HASIL
---------------
- Pilih suara cowok dan cewek dari daftar provider yang sedang dipilih.
- Tempo generate/preview per suara 0,70–2,00x; coba 0,85 untuk lebih lambat.
- Tempo Microsoft diterapkan melalui SSML prosody rate;
  VOICEVOX memakai pengaturan speed_scale asli model.
- Kecepatan putar 0,50–2,00x mengubah pemutaran, bukan file unduhan.
- Pengaturan tempo disimpan di browser. File akhir WAV mono 24 kHz.
- Batas subtitle berasal dari sampel audio aktual setelah pengaturan tempo.
- Durasi adalah target prompt, bukan pemotongan/pemanjangan audio paksa.

SERTIFIKAT HTTPS
----------------
- Node 22.19+ menggabungkan CA bawaan Node, CA tambahan bila dikonfigurasi,
  dan sertifikat tepercaya Windows sebelum membuka koneksi TTS.
- Memperbaiki UNABLE_TO_VERIFY_LEAF_SIGNATURE jika penerbit dipercaya Windows.
- Validasi sertifikat dan hostname tetap aktif; trust store Windows tidak diubah.
- Pada Node lama, TLS normal tetap digunakan. Upgrade Node bila sertifikat
  sistem diperlukan.

VERIFIKASI
----------
- npm test: prompt, parser, WAV, validasi API, penggabungan CA, sintesis Microsoft
  offline, dan VOICEVOX CPU (dua suara, tempo, intonasi, gaya, serta kredit).
  Tes engine lokal dilewati jika runtime masing-masing belum terpasang.
- Contoh naskah: examples/dialog-demo.txt.
- Audio sebelum perubahan disimpan di outputs/saved-audio.
