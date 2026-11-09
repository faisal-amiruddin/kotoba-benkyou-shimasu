import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
export const offlineConfigured = process.platform === 'win32' && existsSync(path.join(root, '.runtime/microsoft-offline/adapter/x64/NaturalVoiceSAPIAdapter.dll'));
let child;
let serial = Promise.resolve();
let nextId = 0;
const pending = new Map();

function worker() {
  if (child) return child;
  const executable = path.join(process.env.SystemRoot || 'C:\\Windows', 'System32/WindowsPowerShell/v1.0/powershell.exe');
  const current = spawn(executable, ['-NoLogo', '-NoProfile', '-NonInteractive', '-File', path.join(root, 'lib/offline-speech.ps1')], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  child = current;
  let output = '';
  let diagnostics = '';
  const fail = error => {
    if (child !== current) return;
    child = undefined;
    for (const item of pending.values()) { clearTimeout(item.timer); item.reject(error); }
    pending.clear();
    current.kill();
  };
  current.stdout.setEncoding('utf8');
  current.stdout.on('data', data => {
    output += data;
    if (output.length > 128 * 1024 * 1024) { fail(new Error('Audio offline terlalu besar. Pendekkan kalimat.')); current.kill(); return; }
    let end;
    while ((end = output.indexOf('\n')) >= 0) {
      const line = output.slice(0, end).trim();
      output = output.slice(end + 1);
      if (!line) continue;
      try {
        const message = JSON.parse(line);
        const item = pending.get(message.id);
        if (!item) continue;
        pending.delete(message.id);
        clearTimeout(item.timer);
        if (message.error) item.reject(new Error(`Microsoft offline: ${message.error}`));
        else item.resolve(message);
      } catch { fail(new Error('Respons Microsoft offline tidak valid.')); current.kill(); }
    }
  });
  current.stderr.on('data', data => { diagnostics = (diagnostics + data).slice(-2000); });
  current.stdin.on('error', fail);
  current.on('error', fail);
  current.on('exit', () => fail(new Error(`Layanan Microsoft offline berhenti.${diagnostics ? ' ' + diagnostics.trim() : ''}`)));
  return current;
}

function request(data) {
  const task = serial.then(() => new Promise((resolve, reject) => {
    if (!offlineConfigured) { reject(new Error('Microsoft offline belum terpasang di laptop ini.')); return; }
    const current = worker();
    const id = ++nextId;
    const timer = setTimeout(() => {
      pending.delete(id);
      reject(new Error('Microsoft offline melewati batas waktu 90 detik.'));
      if (child === current) child = undefined;
      current.kill();
    }, 90000);
    pending.set(id, {resolve, reject, timer});
    current.stdin.write(JSON.stringify({...data, id}) + '\n');
  }));
  serial = task.catch(() => {});
  return task;
}

export async function getOfflineVoices() {
  return (await request({op: 'voices'})).voices;
}

export async function synthesizeOffline(text, voice, speed) {
  const {audio} = await request({op: 'synthesize', text, voice, speed});
  const pcm = Buffer.from(audio, 'base64');
  if (!pcm.length || pcm.length % 2) throw new Error('Microsoft offline mengembalikan audio kosong/tidak valid.');
  const header = Buffer.alloc(44);
  header.write('RIFF', 0); header.writeUInt32LE(pcm.length + 36, 4); header.write('WAVEfmt ', 8);
  header.writeUInt32LE(16, 16); header.writeUInt16LE(1, 20); header.writeUInt16LE(1, 22);
  header.writeUInt32LE(24000, 24); header.writeUInt32LE(48000, 28); header.writeUInt16LE(2, 32); header.writeUInt16LE(16, 34);
  header.write('data', 36); header.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([header, pcm]);
}

export function closeOfflineSpeech() { child?.stdin.end(); }
