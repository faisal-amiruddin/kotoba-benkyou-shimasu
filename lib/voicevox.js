import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const python = path.join(root, '.runtime/voicevox/venv', process.platform === 'win32' ? 'Scripts/python.exe' : 'bin/python');
export const voicevoxConfigured = existsSync(python) && existsSync(path.join(root, '.runtime/voicevox/install.json'));
let child;
let serial = Promise.resolve();
let nextId = 0;
let pending;

function worker() {
  if (child) return child;
  const current = spawn(python, ['-u', path.join(root, 'lib/voicevox-worker.py')], {
    windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'],
    env: { ...process.env, PYTHONUTF8: '1', RUST_LOG: 'error' },
  });
  child = current;
  let output = '';
  let diagnostics = '';
  const fail = error => {
    if (child !== current) return;
    child = undefined;
    if (pending) { clearTimeout(pending.timer); pending.reject(error); pending = undefined; }
    current.kill();
  };
  current.stdout.setEncoding('utf8');
  current.stdout.on('data', data => {
    output += data;
    if (output.length > 128 * 1024 * 1024) { fail(new Error('Audio VOICEVOX terlalu besar. Pendekkan kalimat.')); return; }
    let end;
    while ((end = output.indexOf('\n')) >= 0) {
      const line = output.slice(0, end).trim();
      output = output.slice(end + 1);
      if (!line) continue;
      let message;
      try { message = JSON.parse(line); }
      catch { fail(new Error('Respons VOICEVOX tidak valid.')); return; }
      if (child !== current || !pending || message.id !== pending.id) continue;
      const item = pending;
      pending = undefined;
      clearTimeout(item.timer);
      if (message.error) item.reject(new Error(`VOICEVOX: ${message.error}`));
      else item.resolve(message);
    }
  });
  current.stderr.on('data', data => { diagnostics = (diagnostics + data).slice(-2000); });
  current.stdin.on('error', fail);
  current.on('error', fail);
  current.on('exit', () => fail(new Error(`Layanan VOICEVOX berhenti.${diagnostics ? ' ' + diagnostics.trim() : ''}`)));
  return current;
}

function request(data) {
  const task = serial.then(() => new Promise((resolve, reject) => {
    if (!voicevoxConfigured) { reject(new Error('VOICEVOX belum terpasang di laptop ini.')); return; }
    const current = worker();
    const id = ++nextId;
    const timer = setTimeout(() => {
      pending = undefined;
      if (child === current) child = undefined;
      reject(new Error('VOICEVOX melewati batas waktu 180 detik. Pendekkan kalimat.'));
      current.kill();
    }, 180000);
    pending = {id, resolve, reject, timer};
    current.stdin.write(JSON.stringify({...data, id}) + '\n');
  }));
  serial = task.catch(() => {});
  return task;
}

export async function getVoicevoxVoices() { return (await request({op: 'voices'})).voices; }
export async function synthesizeVoicevox(text, voice, speed = 1, intonation = 1, pronunciation = 'natural') {
  const {audio} = await request({op: 'synthesize', text, voice, speed, intonation, pronunciation});
  const wav = Buffer.from(audio, 'base64');
  if (wav.length <= 44 || wav.toString('ascii', 0, 4) !== 'RIFF' || wav.toString('ascii', 8, 12) !== 'WAVE') throw new Error('VOICEVOX mengembalikan WAV kosong/tidak valid.');
  return wav;
}
export function closeVoicevox() { child?.stdin.end(); }
