import http from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { MsEdgeTTS, OUTPUT_FORMAT } from 'msedge-tts';
import { configureSystemTrust } from './lib/tls-trust.js';
import { offlineConfigured, getOfflineVoices, synthesizeOffline } from './lib/offline-speech.js';
import { voicevoxConfigured, getVoicevoxVoices, synthesizeVoicevox } from './lib/voicevox.js';

const systemCertificateCount = configureSystemTrust();

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const HOST = '127.0.0.1';
const PORT = Number(process.env.PORT || 3210);
const INDEX_PATH = path.join(__dirname, 'public', 'index.html');
const MAX_BODY_BYTES = 1024 * 1024;
const MAX_TEXT_CHARS = 4000;
const voiceCache = { at: 0, items: [] };

function send(res, status, body, type = 'application/json; charset=utf-8') {
  res.writeHead(status, {
    'Content-Type': type,
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff'
  });
  res.end(body);
}

async function readJson(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw new Error('Request terlalu besar.');
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw new Error('Format JSON tidak valid.');
  }
}

function cleanMp3(buffer) {
  // Strip ID3v2 header/footer and ID3v1 tail if the service happens to include them.
  let start = 0;
  let end = buffer.length;
  if (end >= 10 && buffer.toString('ascii', 0, 3) === 'ID3') {
    const b = buffer;
    const size = ((b[6] & 0x7f) << 21) | ((b[7] & 0x7f) << 14) | ((b[8] & 0x7f) << 7) | (b[9] & 0x7f);
    start = Math.min(end, 10 + size + ((b[5] & 0x10) ? 10 : 0));
  }
  if (end - start >= 128 && buffer.toString('ascii', end - 128, end - 125) === 'TAG') {
    end -= 128;
  }
  return buffer.subarray(start, end);
}

async function synthesizeOnce(text, voice, speed = 1) {
  const tts = new MsEdgeTTS({ connectionTimeoutMs: 15000, synthesisTimeoutMs: 90000 });
  try {
    await tts.setMetadata(voice, OUTPUT_FORMAT.AUDIO_24KHZ_48KBITRATE_MONO_MP3);
    const { audioStream } = await tts.toStream(text, { rate: speed });
    const pieces = [];
    for await (const part of audioStream) pieces.push(Buffer.from(part));
    const audio = cleanMp3(Buffer.concat(pieces));
    if (audio.length < 100) throw new Error('Microsoft Edge tidak mengembalikan data audio. Periksa nama voice dan koneksi internet.');
    return audio;
  } finally {
    try { tts.close(); } catch { /* connection may already be closed */ }
  }
}

async function retryEdge(operation) {
  for (let attempt = 0; ; attempt++) {
    try { return await operation(); }
    catch (error) {
      const transient = /ECONNRESET|ETIMEDOUT|EPIPE|EAI_AGAIN/.test(`${error.code || ''} ${error.cause?.code || ''} ${error.message || ''}`);
      if (!transient || attempt >= 2) throw error;
      await new Promise(resolve => setTimeout(resolve, 500 * (attempt + 1)));
    }
  }
}

async function synthesizeMp3(text, voice, speed = 1) {
  return retryEdge(() => synthesizeOnce(text, voice, speed));
}

async function getJapaneseVoices() {
  const now = Date.now();
  if (voiceCache.items.length && now - voiceCache.at < 10 * 60 * 1000) return voiceCache.items;
  const tts = new MsEdgeTTS({ connectionTimeoutMs: 15000, synthesisTimeoutMs: 30000 });
  try {
    const voices = await retryEdge(() => tts.getVoices());
    const japanese = voices
      .filter(v => v.Locale === 'ja-JP' && /Neural$/i.test(v.ShortName || ''))
      .map(v => ({
        ShortName: v.ShortName,
        FriendlyName: v.FriendlyName,
        Gender: v.Gender,
        Locale: v.Locale
      }))
      .sort((a, b) => a.Gender.localeCompare(b.Gender) || a.ShortName.localeCompare(b.ShortName));
    if (!japanese.length) throw new Error('Tidak ada voice Neural Jepang yang dikembalikan Microsoft Edge.');
    voiceCache.items = japanese;
    voiceCache.at = now;
    return japanese;
  } finally {
    try { tts.close(); } catch { /* no active socket */ }
  }
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${HOST}:${PORT}`);

    if (req.method === 'GET' && url.pathname === '/') {
      const html = await readFile(INDEX_PATH);
      return send(res, 200, html, 'text/html; charset=utf-8');
    }

    if (req.method === 'GET' && ['/app.js', '/platform.js'].includes(url.pathname)) {
      return send(res, 200, await readFile(path.join(__dirname, 'public', url.pathname.slice(1))), 'text/javascript; charset=utf-8');
    }

    if (req.method === 'GET' && url.pathname === '/api/health') {
      return send(res, 200, JSON.stringify({ ok: true, engine: 'msedge-tts@2.1.1', systemCertificateCount } ));
    }

    if (req.method === 'GET' && url.pathname === '/api/engines') {
      let offlineAvailable = false;
      let offlineError = '';
      let voicevoxAvailable = false;
      let voicevoxError = '';
      if (offlineConfigured) {
        try { offlineAvailable = (await getOfflineVoices()).length === 2; }
        catch (error) { offlineError = error.message; }
      }
      if (voicevoxConfigured) {
        try {
          const voices = await getVoicevoxVoices();
          voicevoxAvailable = voices.some(v => v.Gender === 'Male') && voices.some(v => v.Gender === 'Female');
        } catch (error) { voicevoxError = error.message; }
      }
      return send(res, 200, JSON.stringify({
        defaultEngine: voicevoxAvailable ? 'voicevox' : 'online',
        offlineAvailable, offlineError, voicevoxAvailable, voicevoxError
      }));
    }

    if (req.method === 'GET' && url.pathname === '/api/voices') {
      const engine = url.searchParams.get('engine') || 'online';
      if (!['online', 'offline', 'voicevox'].includes(engine)) return send(res, 400, JSON.stringify({ error: 'Mode suara tidak valid.' }));
      const voices = engine === 'voicevox' ? await getVoicevoxVoices() : engine === 'offline' ? await getOfflineVoices() : await getJapaneseVoices();
      return send(res, 200, JSON.stringify({ voices }));
    }

    if (req.method === 'POST' && url.pathname === '/api/synthesize') {
      const data = await readJson(req);
      const text = typeof data.text === 'string' ? data.text.trim() : '';
      const voice = typeof data.voice === 'string' ? data.voice.trim() : '';
      if (!text) return send(res, 400, JSON.stringify({ error: 'Teks tidak boleh kosong.' }));
      if (text.length > MAX_TEXT_CHARS) return send(res, 400, JSON.stringify({ error: `Maksimal ${MAX_TEXT_CHARS} karakter per baris.` }));
      const speed = data.speed === undefined ? 1 : data.speed;
      if (typeof speed !== 'number' || !Number.isFinite(speed) || speed < 0.7 || speed > 2) {
        return send(res, 400, JSON.stringify({ error: 'Tempo harus berupa angka antara 0,70× dan 2,00×.' }));
      }
      const engine = data.engine || 'online';
      if (!['online', 'offline', 'voicevox'].includes(engine)) return send(res, 400, JSON.stringify({ error: 'Mode suara tidak valid.' }));
      if (data.provider && data.provider !== (engine === 'voicevox' ? 'voicevox' : 'microsoft')) return send(res, 400, JSON.stringify({ error: 'Provider suara tidak sesuai dengan mode yang dipilih.' }));
      let intonation = 1;
      const pronunciation = data.pronunciation === undefined ? 'natural' : data.pronunciation;
      if (engine === 'voicevox') {
        if (!['natural', 'original'].includes(pronunciation)) return send(res, 400, JSON.stringify({ error: 'Mode pelafalan tidak valid.' }));
        if (!/^voicevox-\d{1,5}$/.test(voice)) return send(res, 400, JSON.stringify({ error: 'Voice VOICEVOX tidak valid.' }));
        intonation = data.intonation === undefined ? 1 : data.intonation;
        if (typeof intonation !== 'number' || !Number.isFinite(intonation) || intonation < 0 || intonation > 2) return send(res, 400, JSON.stringify({ error: 'Intonasi VOICEVOX harus berupa angka antara 0 dan 2.' }));
        if (!(await getVoicevoxVoices()).some(v => v.ShortName === voice)) return send(res, 400, JSON.stringify({ error: 'Gaya suara VOICEVOX ini belum terpasang.' }));
      } else if (!/^ja-JP-[A-Za-z0-9]+Neural$/.test(voice)) return send(res, 400, JSON.stringify({ error: 'Voice Jepang Neural tidak valid.' }));
      if (engine === 'offline' && !['ja-JP-KeitaNeural', 'ja-JP-NanamiNeural'].includes(voice)) return send(res, 400, JSON.stringify({ error: 'Suara offline yang tersedia: Keita dan Nanami.' }));
      const audio = engine === 'voicevox' ? await synthesizeVoicevox(text, voice, speed, intonation, pronunciation) : engine === 'offline' ? await synthesizeOffline(text, voice, speed) : await synthesizeMp3(text, voice, speed);
      res.writeHead(200, {
        'Content-Type': engine === 'online' ? 'audio/mpeg' : 'audio/wav',
        'Content-Length': audio.length,
        'Cache-Control': 'no-store',
        'X-Content-Type-Options': 'nosniff',
        'X-TTS-Engine': engine === 'voicevox' ? 'voicevox-core@0.17.1' : engine === 'offline' ? 'microsoft-narrator-local' : 'msedge-tts@2.1.1'
      });
      return res.end(audio);
    }

    return send(res, 404, JSON.stringify({ error: 'Endpoint tidak ditemukan.' }));
  } catch (error) {
    console.error(`[${new Date().toISOString()}]`, error);
    if (!res.headersSent) {
      const message = error?.message || 'Terjadi kesalahan pada server TTS.';
      send(res, 502, JSON.stringify({ error: message }));
    } else {
      res.destroy(error);
    }
  }
});

server.listen(PORT, HOST, () => {
  console.log('\nKotoba Studio siap digunakan:');
  console.log(`  http://${HOST}:${server.address().port}`);
  console.log(`Engine: Microsoft Edge Read Aloud${offlineConfigured ? ' + Microsoft Narrator offline' : ''}`);
  if (voicevoxConfigured) console.log('VOICEVOX CORE: CPU / offline');
  console.log('Tekan Ctrl+C untuk menghentikan server.\n');
});
