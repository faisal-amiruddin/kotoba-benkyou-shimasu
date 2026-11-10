import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';

test('TTS API validates requests before generating audio', { timeout: 15000 }, async () => {
  const child = spawn(process.execPath, ['server.js'], { cwd: new URL('..', import.meta.url), env: { ...process.env, PORT: '0' }, stdio: ['ignore', 'pipe', 'pipe'] });
  try {
    const base = await new Promise((resolve, reject) => {
      let output = '';
      child.stdout.on('data', data => { output += data; const match = output.match(/http:\/\/127\.0\.0\.1:\d+/); if (match) resolve(match[0]); });
      child.on('error', reject);
      child.on('exit', code => reject(new Error(`Server exited ${code}`)));
    });
    const post = (route, data) => fetch(base + route, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
    const health = await (await fetch(base + '/api/health')).json();
    assert.equal(health.engine, 'msedge-tts@2.1.1');
    assert.equal(typeof health.systemCertificateCount, 'number');
    const html = await (await fetch(base)).text();
    assert.ok(!/voicestudio|voxcpm2/i.test(html));
    assert.ok(html.includes('id="maleSpeed"'));
    assert.ok(html.includes('value="voicevox"'));
    assert.ok(html.includes('id="voicevoxIntonation"'));
    assert.equal((await fetch(base + '/app.js')).status, 200);
    assert.equal((await fetch(base + '/theme.js')).status, 200);
    assert.equal((await post('/api/voicestudio/voices', {})).status, 404);
    assert.equal((await fetch(base + '/assets/school-bell.mp3')).status, 404);
    for (const speed of [0.5, 2.1, '1', null]) {
      assert.equal((await post('/api/synthesize', { text: 'こんにちは。', voice: 'ja-JP-KeitaNeural', speed })).status, 400);
    }
    assert.equal((await post('/api/synthesize', { text: 'a', voice: 'invalid' })).status, 400);
    assert.equal((await post('/api/synthesize', { text: '', voice: 'ja-JP-KeitaNeural' })).status, 400);
    assert.equal((await post('/api/synthesize', { text: 'a', voice: 'a', provider: 'unknown' })).status, 400);
    assert.equal((await fetch(base + '/api/voices?engine=unknown')).status, 400);
    assert.equal((await post('/api/synthesize', { text: 'こんにちは。', voice: 'ja-JP-KeitaNeural', engine: 'unknown' })).status, 400);
    assert.equal((await post('/api/synthesize', { text: 'こんにちは。', voice: 'ja-JP-OtherNeural', engine: 'offline' })).status, 400);
    assert.equal((await post('/api/synthesize', { text: 'こんにちは。', voice: 'ja-JP-KeitaNeural', engine: 'voicevox' })).status, 400);
    assert.equal((await post('/api/synthesize', { text: 'こんにちは。', voice: 'voicevox-11', engine: 'online' })).status, 400);
    assert.equal((await post('/api/synthesize', { text: 'こんにちは。', voice: 'voicevox-11', engine: 'voicevox', pronunciation: 'invalid' })).status, 400);
    for (const intonation of [-0.1, 2.1, '1', null]) {
      assert.equal((await post('/api/synthesize', { text: 'こんにちは。', voice: 'voicevox-11', engine: 'voicevox', intonation })).status, 400);
    }
  } finally { child.kill(); }
});
