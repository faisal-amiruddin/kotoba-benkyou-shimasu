// ---------- Prompt generator ----------
    const levelEl = document.getElementById('level');
    const durationEl = document.getElementById('duration');
    const topicEl = document.getElementById('topic');
    const promptOutput = document.getElementById('promptOutput');

    const levelGuides = {
      N5: 'Gunakan kosakata sangat dasar dan pola tata bahasa pemula: salam, perkenalan, angka, waktu, keluarga, makanan, tempat, aktivitas harian, です／ます, partikel は・が・を・に・で・へ, dan bentuk kata kerja dasar. Kalimat pendek dan jelas.',
      N4: 'Gunakan kosakata dan tata bahasa dasar tingkat lanjut: bentuk lampau, bentuk て, bentuk biasa, alasan sederhana, keinginan, pengalaman, perbandingan, permintaan, dan percakapan sehari-hari. Hindari struktur yang terlalu kompleks.',
      N3: 'Gunakan tata bahasa dan kosakata tingkat menengah: menyampaikan pendapat, alasan, pengalaman, dugaan, maksud, kontras, serta percakapan yang lebih tersambung. Boleh memakai ungkapan natural sehari-hari, tetapi tetap mudah diikuti pelajar N3.',
      N2: 'Gunakan tata bahasa dan kosakata menengah-atas: nuansa sopan dan kasual, ungkapan idiomatis umum, topik sosial, pekerjaan, budaya, berita ringan, serta argumen dan pendapat yang lebih terstruktur. Jaga dialog tetap alami.',
      N1: 'Gunakan bahasa Jepang tingkat mahir yang natural dan kaya nuansa: kosakata lanjutan, ekspresi idiomatis, implikasi, register, opini abstrak, dan struktur kalimat kompleks. Hindari bahasa yang terasa seperti buku teks; tetap buat percakapan yang masuk akal.'
    };

    function buildPrompt() {
      const duration = Math.max(1, Math.min(90, Number.parseInt(durationEl.value, 10) || 1));
      const level = levelEl.value;
      const topic = topicEl.value.trim();
      const topicLine = topic ? `TEMA UTAMA: ${topic}\nBangun dialog yang konsisten pada tema tersebut.` : 'TEMA UTAMA: Pilih sendiri satu situasi sehari-hari yang menarik dan realistis, sesuai level JLPT.';
      promptOutput.value = `Buat naskah percakapan bahasa Jepang untuk latihan listening dua orang: satu cowok dan satu cewek.

LEVEL: JLPT ${level}
DURASI TARGET: sekitar ${duration} menit saat dibacakan dengan tempo natural dan jelas.
${topicLine}

PANDUAN BAHASA:
${levelGuides[level]}

PANDUAN DIALOG:
- Dialog harus terdengar seperti percakapan nyata antara dua orang yang saling merespons, bukan dua monolog terpisah.
- Buat alur yang jelas: pembukaan, percakapan berkembang, lalu penutup yang wajar.
- Targetkan sekitar ${Math.max(10, duration * 12)} giliran bicara/baris agar durasinya mendekati ${duration} menit. Sesuaikan panjang kalimat dengan tempo bicara natural.
- Variasikan panjang kalimat, pertanyaan, jawaban, reaksi, dan ungkapan spontan yang sesuai level.
- Gunakan tepat dua pembicara: cowok dan cewek. Jangan menambah narator atau karakter ketiga.
- Setiap giliran bicara hanya satu baris. Jangan memecah satu giliran menjadi beberapa baris.
- Gunakan bahasa Jepang yang benar, alami, dan konsisten dengan level JLPT yang diminta.

FORMAT OUTPUT WAJIB:
{cowo[1]} こんにちは。
{romaji[1]} Konnichiwa.
{translate[1]} Halo.
{cewe[2]} 今日は何をしますか？
{romaji[2]} Kyou wa nani o shimasu ka?
{translate[2]} Hari ini mau melakukan apa?

ATURAN FORMAT:
- Nomori setiap giliran bicara secara berurutan mulai 1, terus bertambah untuk kedua pembicara (bukan nomor terpisah per pembicara).
- Setiap giliran terdiri dari tiga baris: {cowo[n]} atau {cewe[n]} berisi kalimat Jepang, {romaji[n]} berisi bacaan latin, dan {translate[n]} berisi terjemahan bahasa Indonesia yang alami.
- Ketiga label dalam satu giliran harus memakai nomor yang sama. Semua giliran WAJIB memiliki romaji dan terjemahan.
- Hanya teks Jepang di baris pembicara yang akan dibacakan. Jangan sisipkan romaji atau terjemahan di baris itu.
- Gunakan romanisasi Hepburn, termasuk bacaan angka dan kanji yang sesuai konteks.
- Jangan menulis judul, bullet, markdown, code fence, arahan adegan, catatan, atau penjelasan tambahan.
- Output hanya naskah dalam format di atas.`;
    }

    [levelEl, durationEl, topicEl].forEach(el => el.addEventListener('input', buildPrompt));
    [levelEl, durationEl].forEach(el => el.addEventListener('change', buildPrompt));
    buildPrompt();

    document.getElementById('copyPrompt').addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(promptOutput.value);
        flashButton('copyPrompt', '✓ Copied');
      } catch (err) {
        promptOutput.focus(); promptOutput.select();
        document.execCommand('copy');
        flashButton('copyPrompt', '✓ Copied');
      }
    });

    function flashButton(id, label) {
      const button = document.getElementById(id);
      const old = button.innerHTML;
      button.textContent = label;
      setTimeout(() => { button.innerHTML = old; }, 1400);
    }

    // ---------- Dialogue parser / live preview ----------
    const dialogInput = document.getElementById('dialogInput');
    const parseSummary = document.getElementById('parseSummary');
    const dialogLines = document.getElementById('dialogLines');
    const lineCounter = document.getElementById('lineCounter');
    let parsedLines = [];
    let invalidLines = [];

    function parseDialogue(raw) {
      const cleaned = raw.replace(/^\s*```(?:text|txt)?\s*/i, '').replace(/\s*```\s*$/i, '');
      const lines = cleaned.split(/\r?\n/);
      const parsed = [];
      const invalid = [];
      const byId = new Map();
      const annotations = [];
      let hasNumbered = false;
      let hasLegacy = false;
      lines.forEach((original, index) => {
        const line = original.trim();
        if (!line) return;
        const numbered = line.match(/^\{(cowo|cewe|romaji|translate)\[([1-9]\d*)\]\}\s*(.+)$/i);
        if (numbered) {
          const [, rawLabel, id, text] = numbered;
          const label = rawLabel.toLowerCase();
          if (label === 'romaji' || label === 'translate') {
            annotations.push({ label, id, text: text.trim(), line: index + 1 });
          } else if (byId.has(id) || Number(id) !== parsed.length + 1) {
            invalid.push({ line: index + 1, text: 'Nomor dialog harus unik dan berurutan mulai 1: ' + original });
          } else {
            hasNumbered = true;
            const item = { id, speaker: label, text: text.trim(), romaji: '', translation: '', sourceLine: index + 1 };
            byId.set(id, item);
            parsed.push(item);
          }
          return;
        }
        const match = line.match(/^\s*(?:\{(cowo|cewe)\}|\[(cowo|cewe)\]|(cowo|cewe)\s*:)\s*(.*?)\s*$/i);
        if (!match || !match[4].trim()) {
          invalid.push({ line: index + 1, text: original });
          return;
        }
        hasLegacy = true;
        parsed.push({ speaker: (match[1] || match[2] || match[3]).toLowerCase(), text: match[4].trim(), romaji: '', translation: '' });
      });
      for (const note of annotations) {
        const item = byId.get(note.id);
        const key = note.label === 'romaji' ? 'romaji' : 'translation';
        if (!item || item[key]) invalid.push({ line: note.line, text: 'Nomor anotasi tidak ditemukan atau duplikat: ' + note.label + '[' + note.id + ']' });
        else item[key] = note.text;
      }
      if (hasNumbered && hasLegacy) invalid.push({ line: 1, text: 'Jangan campur format bernomor dan format lama.' });
      for (const item of byId.values()) {
        if (!item.romaji || !item.translation) invalid.push({ line: item.sourceLine, text: 'Dialog [' + item.id + '] harus memiliki {romaji[' + item.id + ']} dan {translate[' + item.id + ']}.' });
      }
      return { parsed, invalid };
    }

    function renderDialogue() {
      const result = parseDialogue(dialogInput.value);
      parsedLines = result.parsed;
      invalidLines = result.invalid;
      const maleCount = parsedLines.filter(x => x.speaker === 'cowo').length;
      const femaleCount = parsedLines.filter(x => x.speaker === 'cewe').length;
      parseSummary.innerHTML = '';
      if (!dialogInput.value.trim()) {
        parseSummary.innerHTML = '<span class="summary-pill">Belum ada dialog</span>';
        dialogLines.innerHTML = '<div class="empty-lines">Dialog yang kamu tempel akan muncul di sini.</div>';
      } else {
        parseSummary.innerHTML = `<span class="summary-pill">${parsedLines.length} baris valid</span><span class="summary-pill male">♂ ${maleCount} cowok</span><span class="summary-pill female">♀ ${femaleCount} cewek</span>${invalidLines.length ? `<span class="summary-pill error">${invalidLines.length} baris perlu diperbaiki</span>` : ''}`;
        if (parsedLines.length) {
          dialogLines.innerHTML = parsedLines.slice(0, 100).map(item => `<div class="dialog-line"><div class="speaker ${item.speaker === 'cowo' ? 'male' : 'female'}">${item.speaker === 'cowo' ? '♂ COWO' : '♀ CEWE'}</div><div class="jp-line"></div></div>`).join('');
          [...dialogLines.querySelectorAll('.jp-line')].forEach((node, i) => {
            const item = parsedLines[i];
            node.textContent = item.text;
            for (const text of [item.romaji, item.translation]) {
              if (!text) continue;
              const detail = document.createElement('div');
              detail.className = 'line-detail';
              detail.textContent = text;
              node.appendChild(detail);
            }
          });
        } else {
          dialogLines.innerHTML = '<div class="empty-lines">Belum ada baris dengan format yang dikenali.</div>';
        }
        if (invalidLines.length) {
          const details = invalidLines.slice(0, 4).map(x => `Baris ${x.line}: ${x.text}`).join('\n');
          parseSummary.title = details + (invalidLines.length > 4 ? '\n...' : '');
        } else parseSummary.removeAttribute('title');
      }
      lineCounter.textContent = `${parsedLines.length} lines`;
      return { parsed: parsedLines, invalid: invalidLines };
    }
    dialogInput.addEventListener('input', renderDialogue);
    renderDialogue();

    // ---------- msedge-tts local API ----------
    const maleVoiceEl = document.getElementById('maleVoice');
    const femaleVoiceEl = document.getElementById('femaleVoice');
    const statusEl = document.getElementById('statusText');
    const generateBtn = document.getElementById('generateAudio');
    const progressWrap = document.getElementById('progressWrap');
    const progressBar = document.getElementById('progressBar');
    const progressLabel = document.getElementById('progressLabel');
    const progressPercent = document.getElementById('progressPercent');
    const resultPanel = document.getElementById('resultPanel');
    const audioElement = document.getElementById('audioPlayer');
    const audioPlayer = window.KotobaMobile?.createPlayer?.(audioElement) || audioElement;
    const downloadAudio = document.getElementById('downloadAudio');
    const voiceSearchStatus = document.getElementById('voiceSearchStatus');
    const reloadVoicesEl = document.getElementById('reloadVoices');
    const speedEls = {
      cowo: document.getElementById('maleSpeed'),
      cewe: document.getElementById('femaleSpeed')
    };
    const playbackSpeedEl = document.getElementById('playbackSpeed');
    try {
      const saved = JSON.parse(localStorage.getItem('kotoba-tempo') || '{}');
      for (const [speaker, el] of Object.entries(speedEls)) {
        if (typeof saved[speaker] === 'number' && saved[speaker] >= 0.7 && saved[speaker] <= 2) el.value = saved[speaker];
      }
      if ([...playbackSpeedEl.options].some(option => option.value === String(saved.playback))) playbackSpeedEl.value = saved.playback;
    } catch { /* Storage is optional. */ }
    function saveTempo() {
      const saved = { playback: Number(playbackSpeedEl.value) };
      for (const [speaker, el] of Object.entries(speedEls)) {
        const speed = Number(el.value);
        if (el.value.trim() && Number.isFinite(speed) && speed >= 0.7 && speed <= 2) saved[speaker] = speed;
      }
      try { localStorage.setItem('kotoba-tempo', JSON.stringify(saved)); } catch { /* Storage is optional. */ }
    }
    function speechSpeed(speaker) {
      const speed = Number(speedEls[speaker].value);
      if (!speedEls[speaker].value.trim() || !Number.isFinite(speed) || speed < 0.7 || speed > 2) {
        throw new Error(`Tempo ${speaker} harus antara 0,70× dan 2,00×.`);
      }
      return speed;
    }
    for (const el of Object.values(speedEls)) el.addEventListener('change', saveTempo);
    function applyPlaybackSpeed() {
      audioPlayer.defaultPlaybackRate = Number(playbackSpeedEl.value);
      audioPlayer.playbackRate = Number(playbackSpeedEl.value);
      audioPlayer.preservesPitch = true;
    }
    playbackSpeedEl.addEventListener('change', () => { applyPlaybackSpeed(); saveTempo(); });
    audioPlayer.addEventListener('loadedmetadata', applyPlaybackSpeed);
    applyPlaybackSpeed();
    let voiceLoadVersion = 0;
    let isLoadingVoices = false;
    let isPreviewing = false;
    let previewPlayer = null;
    let currentAudioUrl = null;
    let isGenerating = false;
    let maleVoices = [];
    let femaleVoices = [];
    let timeline = [];
    let activeSubtitle = -1;
    let subtitleFrame = null;

    function syncSubtitle() {
      const time = audioPlayer.currentTime;
      const index = timeline.findIndex(line => time >= line.start && time < line.end);
      const next = index < 0 && timeline.length && time >= timeline[timeline.length - 1].end ? timeline.length - 1 : index;
      if (next === activeSubtitle) return;
      activeSubtitle = next;
      const line = timeline[next];
      document.getElementById('subtitleSpeaker').textContent = line ? `${line.speaker === 'cowo' ? 'Cowok' : 'Cewek'} · ${line.id || ''}` : '';
      document.getElementById('subtitleJapanese').textContent = line?.text || '';
      document.getElementById('subtitleRomaji').textContent = line?.romaji || (line ? 'Romaji belum disertakan pada naskah.' : '');
      document.getElementById('subtitleTranslation').textContent = line?.translation || (line ? 'Terjemahan belum disertakan pada naskah.' : '');
    }
    function tickSubtitle() {
      syncSubtitle();
      if (!audioPlayer.paused && !audioPlayer.ended) subtitleFrame = requestAnimationFrame(tickSubtitle);
    }
    for (const event of ['timeupdate', 'seeking', 'seeked', 'loadedmetadata', 'pause', 'ended']) audioPlayer.addEventListener(event, syncSubtitle);
    audioPlayer.addEventListener('play', () => {
      if (previewPlayer) previewPlayer.pause();
      cancelAnimationFrame(subtitleFrame);
      tickSubtitle();
    });

    // Encode every decoded sentence into one PCM stream. Subtitle boundaries use
    // the exact same sample offsets as playback, including provider silence.
    function pcmChunk(buffer) {
      const bytes = new Uint8Array(buffer.length * 2);
      const view = new DataView(bytes.buffer);
      const channels = Array.from({ length: buffer.numberOfChannels }, (_, i) => buffer.getChannelData(i));
      for (let i = 0; i < buffer.length; i++) {
        let sample = 0;
        for (const channel of channels) sample += channel[i] / channels.length;
        sample = Math.max(-1, Math.min(1, sample));
        view.setInt16(i * 2, Math.round(sample * (sample < 0 ? 32768 : 32767)), true);
      }
      return bytes;
    }
    function mergeWav(chunks, sampleRate) {
      const size = chunks.reduce((sum, chunk) => sum + chunk.byteLength, 0);
      if (size > 0xffffffff - 36) throw new Error('Audio terlalu besar untuk format WAV. Kurangi panjang dialog.');
      const header = new ArrayBuffer(44);
      const view = new DataView(header);
      const write = (offset, text) => [...text].forEach((char, i) => view.setUint8(offset + i, char.charCodeAt(0)));
      write(0, 'RIFF'); view.setUint32(4, size + 36, true); write(8, 'WAVE'); write(12, 'fmt ');
      view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true);
      view.setUint32(24, sampleRate, true); view.setUint32(28, sampleRate * 2, true);
      view.setUint16(32, 2, true); view.setUint16(34, 16, true); write(36, 'data'); view.setUint32(40, size, true);
      return new Blob([header, ...chunks], { type: 'audio/wav' });
    }

    function setStatus(message, isError = false) {
      statusEl.textContent = message;
      statusEl.classList.toggle('error', isError);
    }

    async function apiRequest(url, options = {}) {
      let response;
      try {
        response = window.KotobaMobile ? await window.KotobaMobile.request(url, options) : await fetch(url, options);
      } catch (error) {
        if (window.KotobaMobile) throw new Error(error.message || 'Layanan audio lokal di HP belum siap.');
        throw new Error('Tidak bisa menghubungi server lokal. Jalankan start.bat terlebih dahulu dan buka alamat http://127.0.0.1:3210.');
      }
      if (!response.ok) {
        const body = await response.text();
        let message = body;
        try { message = JSON.parse(body).error || body; } catch (_) {}
        throw new Error(message || `Request gagal (${response.status}).`);
      }
      return response;
    }

    function fillVoiceSelect(select, voices, defaultVoice) {
      select.innerHTML = '';
      voices.forEach(voice => {
        const option = document.createElement('option');
        option.value = voice.ShortName;
        option.textContent = voice.Credit ? voice.FriendlyName : `${voice.FriendlyName || voice.ShortName} · ${voice.ShortName}`;
        select.appendChild(option);
      });
      if (voices.some(v => v.ShortName === defaultVoice)) select.value = defaultVoice;
    }

    const engineEl = document.getElementById('speechEngine');
    const intonationEl = document.getElementById('voicevoxIntonation');
    const pronunciationEl = document.getElementById('voicevoxPronunciation');
    let creditsUrl = null;
    function showCredits(credits) {
      const creditsEl = document.getElementById('resultCredits');
      const creditsDownload = document.getElementById('downloadCredits');
      creditsEl.textContent = credits ? `Kredit saat membagikan audio: ${credits.replace(/\n/g, ' · ')}` : '';
      creditsEl.style.display = credits ? 'block' : 'none';
      creditsDownload.style.display = credits ? 'inline-flex' : 'none';
      if (creditsUrl) URL.revokeObjectURL(creditsUrl);
      creditsUrl = credits ? URL.createObjectURL(new Blob([credits + '\n'], {type:'text/plain;charset=utf-8'})) : null;
      if (creditsUrl) creditsDownload.href = creditsUrl;
    }
    try {
      const saved = localStorage.getItem('kotoba-voicevox-intonation');
      if (saved !== null && Number.isFinite(Number(saved)) && Number(saved) >= 0 && Number(saved) <= 2) intonationEl.value = saved;
    } catch {}
    try { const saved = localStorage.getItem('kotoba-pronunciation'); if (['natural','original'].includes(saved)) pronunciationEl.value = saved; } catch {}
    pronunciationEl.addEventListener('change', () => { try { localStorage.setItem('kotoba-pronunciation', pronunciationEl.value); } catch {} });
    function speechIntonation() {
      const value = Number(intonationEl.value);
      if (!intonationEl.value.trim() || !Number.isFinite(value) || value < 0 || value > 2) throw new Error('Variasi intonasi VOICEVOX harus antara 0 dan 2.');
      return value;
    }
    intonationEl.addEventListener('change', () => {
      try { localStorage.setItem('kotoba-voicevox-intonation', String(speechIntonation())); } catch {}
    });
    function updateEngineNote() {
      document.getElementById('voicevoxControls').style.display = engineEl.value === 'voicevox' ? 'block' : 'none';
      document.getElementById('engineNote').textContent = engineEl.value === 'voicevox'
        ? `VOICEVOX berjalan di ${window.KotobaMobile ? 'HP' : 'laptop'} tanpa internet. Pilih karakter dan gaya bicara, lalu gunakan Preview untuk membandingkan. Kredit karakter disertakan pada hasil audio.`
        : engineEl.value === 'offline'
        ? 'Nanami & Keita terpasang di laptop. Preview dan generate audio dapat digunakan tanpa internet.'
        : 'Microsoft Edge Read Aloud · tanpa API key Azure. Koneksi internet diperlukan.';
    }
    async function loadEngines() {
      isLoadingVoices = true;
      generateBtn.disabled = true;
      engineEl.disabled = true;
      try {
        const data = await (await apiRequest('/api/engines')).json();
        engineEl.querySelector('[value="online"]').disabled = data.onlineAvailable === false;
        engineEl.querySelector('[value="offline"]').disabled = !data.offlineAvailable;
        engineEl.querySelector('[value="voicevox"]').disabled = !data.voicevoxAvailable;
        let saved;
        try { saved = localStorage.getItem('kotoba-speech-engine'); } catch {}
        const available = [...(data.onlineAvailable !== false ? ['online'] : []), ...(data.offlineAvailable ? ['offline'] : []), ...(data.voicevoxAvailable ? ['voicevox'] : [])];
        engineEl.value = available.includes(saved) ? saved : available.includes(data.defaultEngine) ? data.defaultEngine : 'online';
        engineEl.title = [data.offlineError, data.voicevoxError].filter(Boolean).join('\n');
      } catch (error) {
        engineEl.value = 'online';
        engineEl.title = error.message;
      } finally {
        engineEl.disabled = false;
        await loadVoices();
      }
    }
    engineEl.addEventListener('change', () => {
      try { localStorage.setItem('kotoba-speech-engine', engineEl.value); } catch {}
      loadVoices();
    });

    async function loadVoices() {
      const version = ++voiceLoadVersion;
      const engine = engineEl.value;
      updateEngineNote();
      isLoadingVoices = true;
      generateBtn.disabled = true;
      maleVoiceEl.innerHTML = femaleVoiceEl.innerHTML = '';
      voiceSearchStatus.textContent = 'Mengambil daftar suara…';
      voiceSearchStatus.removeAttribute('title');
      const fallbackMale = [
        { ShortName: 'ja-JP-KeitaNeural', FriendlyName: 'Keita', Gender: 'Male' }
      ];
      const fallbackFemale = [
        { ShortName: 'ja-JP-NanamiNeural', FriendlyName: 'Nanami', Gender: 'Female' }
      ];
      try {
        const response = await apiRequest(`/api/voices?engine=${engine}`);
        const data = await response.json();
        if (version !== voiceLoadVersion) return;
        maleVoices = data.voices.filter(v => v.Locale === 'ja-JP' && String(v.Gender).toLowerCase() === 'male');
        femaleVoices = data.voices.filter(v => v.Locale === 'ja-JP' && String(v.Gender).toLowerCase() === 'female');
        if (!maleVoices.length || !femaleVoices.length) throw new Error('Daftar suara Jepang belum memuat suara cowok dan cewek.');
        fillVoiceSelect(maleVoiceEl, maleVoices, engine === 'voicevox' ? 'voicevox-11' : 'ja-JP-KeitaNeural');
        fillVoiceSelect(femaleVoiceEl, femaleVoices, engine === 'voicevox' ? 'voicevox-2' : 'ja-JP-NanamiNeural');
        voiceSearchStatus.textContent = `${maleVoices.length} pilihan suara/gaya pria dan ${femaleVoices.length} pilihan suara/gaya wanita tersedia · ${engine === 'voicevox' ? 'VOICEVOX offline' : engine === 'offline' ? 'Microsoft lokal, tanpa internet' : 'Microsoft Edge online'}.`;
      } catch (error) {
        if (version !== voiceLoadVersion) return;
        if (engine !== 'online') {
          maleVoices = femaleVoices = [];
          voiceSearchStatus.textContent = `Suara ${engine === 'voicevox' ? 'VOICEVOX' : 'offline'} belum siap. ${error.message}`;
          return;
        }
        maleVoices = fallbackMale;
        femaleVoices = fallbackFemale;
        fillVoiceSelect(maleVoiceEl, maleVoices, 'ja-JP-KeitaNeural');
        fillVoiceSelect(femaleVoiceEl, femaleVoices, 'ja-JP-NanamiNeural');
        voiceSearchStatus.textContent = 'Tidak dapat mengambil daftar voice sekarang. Menampilkan pilihan default; preview akan memastikan apakah voice tersedia.';
        voiceSearchStatus.title = error.message;
      } finally {
        if (version === voiceLoadVersion) { isLoadingVoices = false; generateBtn.disabled = isGenerating || !maleVoiceEl.value || !femaleVoiceEl.value; }
      }
    }

    reloadVoicesEl.addEventListener('click', loadVoices);

    async function synthesize(text, voice, speed = 1) {
      const response = await apiRequest('/api/synthesize', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text, voice, speed, engine: engineEl.value, ...(engineEl.value === 'voicevox' ? { intonation: speechIntonation(), pronunciation: pronunciationEl.value } : {}) })
      });
      return await response.blob();
    }

    async function previewVoice(speaker) {
      if (isGenerating || isLoadingVoices || isPreviewing) return;
      isPreviewing = true;
      const version = voiceLoadVersion;
      const button = document.getElementById(speaker === 'cowo' ? 'previewMale' : 'previewFemale');
      const original = button.innerHTML;
      try {
        button.disabled = true;
        button.textContent = 'Membuat preview…';
        const voice = speaker === 'cowo' ? maleVoiceEl.value : femaleVoiceEl.value;
        if (!voice) throw new Error('Hubungkan layanan dan pilih suara terlebih dahulu.');
        const sample = speaker === 'cowo'
          ? 'こんにちは。今日は一緒に日本語を勉強しましょう。'
          : 'こんにちは。はい、よろしくお願いします。';
        setStatus(`Membuat preview ${speaker === 'cowo' ? 'suara cowok' : 'suara cewek'} dengan ${voice}…`);
        const blob = await synthesize(sample, voice, speechSpeed(speaker));
        if (version !== voiceLoadVersion) return;
        const url = URL.createObjectURL(blob);
        const player = new Audio(url);
        if (previewPlayer) previewPlayer.pause();
        audioPlayer.pause();
        previewPlayer = player;
        player.onended = () => URL.revokeObjectURL(url);
        player.onerror = () => URL.revokeObjectURL(url);
        await player.play();
        setStatus(`Preview diputar menggunakan ${voice}.`);
      } catch (err) {
        setStatus(err.message || String(err), true);
      } finally {
        isPreviewing = false;
        button.disabled = false;
        button.innerHTML = original;
      }
    }

    document.getElementById('previewMale').addEventListener('click', () => previewVoice('cowo'));
    document.getElementById('previewFemale').addEventListener('click', () => previewVoice('cewe'));

    generateBtn.addEventListener('click', async () => {
      if (isGenerating || isLoadingVoices) return;
      if (isPreviewing) { setStatus('Tunggu pembuatan preview selesai, lalu generate audio.'); return; }
      const result = renderDialogue();
      if (!dialogInput.value.trim()) { setStatus('Tempel dialog dari LLM terlebih dahulu.', true); return; }
      if (result.invalid.length) { setStatus(`Ada ${result.invalid.length} kesalahan format. ${result.invalid[0].text}`, true); return; }
      if (!result.parsed.length) { setStatus('Tidak ada kalimat dialog yang bisa diproses.', true); return; }
      if (!maleVoiceEl.value || !femaleVoiceEl.value) { setStatus('Hubungkan layanan dan pilih suara terlebih dahulu.', true); return; }
      try {
        for (const speaker of new Set(result.parsed.map(line => line.speaker))) speechSpeed(speaker);
        if (engineEl.value === 'voicevox') speechIntonation();
      } catch (error) { setStatus(error.message, true); return; }

      isGenerating = true;
      if (previewPlayer) previewPlayer.pause();
      audioPlayer.pause();
      for (const el of [engineEl, intonationEl, pronunciationEl, reloadVoicesEl, maleVoiceEl, femaleVoiceEl, ...Object.values(speedEls)]) el.disabled = true;
      generateBtn.disabled = true;
      document.getElementById('previewMale').disabled = true;
      document.getElementById('previewFemale').disabled = true;
      resultPanel.style.display = 'none';
      progressWrap.style.display = 'block';
      progressBar.style.width = '0%';
      progressPercent.textContent = '0%';
      const chunks = [];
      const nextTimeline = [];
      let frames = 0;
      let context;
      try {
        context = new AudioContext({ sampleRate: 24000 });
        const total = result.parsed.length;
        for (let i = 0; i < total; i++) {
          const line = result.parsed[i];
          const voice = line.speaker === 'cowo' ? maleVoiceEl.value : femaleVoiceEl.value;
          progressLabel.textContent = `Membuat audio ${i + 1} dari ${total} · ${line.speaker}`;
          setStatus(`Menyintesis baris ${i + 1}/${total} dengan ${voice}…`);
          const blob = await synthesize(line.text, voice, speechSpeed(line.speaker));
          const decoded = await context.decodeAudioData(await blob.arrayBuffer());
          if (!decoded.length) throw new Error(`Audio baris ${i + 1} kosong.`);
          nextTimeline.push({ ...line, start: frames / context.sampleRate, end: (frames + decoded.length) / context.sampleRate });
          frames += decoded.length;
          chunks.push(pcmChunk(decoded));
          const percent = Math.round(((i + 1) / total) * 96);
          progressBar.style.width = percent + '%';
          progressPercent.textContent = percent + '%';
        }

        progressLabel.textContent = 'Menyambungkan audio dan subtitle…';
        setStatus('Semua baris selesai. Menyiapkan audio WAV…');
        const credits = engineEl.value === 'voicevox' ? [...new Set(result.parsed.map(line => {
          const voice = line.speaker === 'cowo' ? maleVoiceEl.value : femaleVoiceEl.value;
          return [...maleVoices, ...femaleVoices].find(item => item.ShortName === voice)?.Credit;
        }).filter(Boolean))].join('\n') : '';
        const mergedBlob = mergeWav(chunks, context.sampleRate);
        if (currentAudioUrl) URL.revokeObjectURL(currentAudioUrl);
        currentAudioUrl = URL.createObjectURL(mergedBlob);
        if (audioPlayer.setSource) await audioPlayer.setSource(mergedBlob, nextTimeline, credits);
        else audioPlayer.src = currentAudioUrl;
        timeline = nextTimeline;
        activeSubtitle = -1;
        syncSubtitle();
        downloadAudio.href = currentAudioUrl;
        const now = new Date();
        const stamp = [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0'), '-', String(now.getHours()).padStart(2, '0'), String(now.getMinutes()).padStart(2, '0')].join('');
        downloadAudio.download = `japanese-dialog-${stamp}.wav`;
        document.getElementById('audioMeta').textContent = `WAV · ${total} kalimat · ${(mergedBlob.size / 1024 / 1024).toFixed(2)} MB`;
        showCredits(credits);
        progressBar.style.width = '100%';
        progressPercent.textContent = '100%';
        progressLabel.textContent = 'Selesai';
        resultPanel.style.display = 'block';
        setStatus('Sukses! Putar audio untuk melihat kalimat Jepang, romaji, dan arti Indonesia yang sesuai.');
      } catch (err) {
        setStatus(err.message || String(err), true);
        if (currentAudioUrl) resultPanel.style.display = 'block';
      } finally {
        if (context) await context.close();
        isGenerating = false;
        for (const el of [engineEl, intonationEl, pronunciationEl, reloadVoicesEl, maleVoiceEl, femaleVoiceEl, ...Object.values(speedEls)]) el.disabled = false;
        generateBtn.disabled = false;
        document.getElementById('previewMale').disabled = false;
        document.getElementById('previewFemale').disabled = false;
      }
    });

    document.getElementById('playAgain').addEventListener('click', () => {
      audioPlayer.currentTime = 0;
      audioPlayer.play();
    });

    window.addEventListener('beforeunload', () => {
      if (currentAudioUrl) URL.revokeObjectURL(currentAudioUrl);
      if (creditsUrl) URL.revokeObjectURL(creditsUrl);
    });

    loadEngines();
    if (audioPlayer.restore) {
      audioPlayer.restore().then(session => {
        if (!session.loaded || !session.timeline?.length) return;
        timeline = session.timeline;
        activeSubtitle = -1;
        syncSubtitle();
        resultPanel.style.display = 'block';
        document.getElementById('audioMeta').textContent = `WAV · ${timeline.length} kalimat · audio terakhir`;
        downloadAudio.href = session.url;
        downloadAudio.download = 'japanese-dialog.wav';
        showCredits(session.credits || '');
      }).catch(() => {});
    }
