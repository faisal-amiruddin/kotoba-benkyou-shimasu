import test from 'node:test';
import assert from 'node:assert/strict';
import { offlineConfigured, getOfflineVoices, synthesizeOffline, closeOfflineSpeech } from '../lib/offline-speech.js';

test('installed Microsoft local voices generate real PCM, preserve Japanese, and honor tempo', {skip: !offlineConfigured, timeout: 30000}, async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = () => { throw new Error('Network access forbidden during offline test'); };
  try {
    const voices = await getOfflineVoices();
    assert.deepEqual(voices.map(v => v.ShortName).sort(), ['ja-JP-KeitaNeural', 'ja-JP-NanamiNeural']);
    assert.ok(voices.every(v => v.Offline && v.Locale === 'ja-JP'));
    for (const voice of voices) {
      const normal = await synthesizeOffline('こんにちは。今日は一緒に日本語を勉強しましょう。', voice.ShortName, 1);
      const slow = await synthesizeOffline('こんにちは。今日は一緒に日本語を勉強しましょう。', voice.ShortName, 0.7);
      assert.equal(normal.toString('ascii', 0, 4), 'RIFF');
      assert.equal(normal.toString('ascii', 8, 12), 'WAVE');
      assert.equal(normal.readUInt32LE(24), 24000);
      assert.equal(normal.readUInt16LE(22), 1);
      assert.equal(normal.readUInt32LE(40), normal.length - 44);
      let peak = 0;
      for (let i = 44; i < normal.length; i += 2) peak = Math.max(peak, Math.abs(normal.readInt16LE(i)));
      assert.ok(peak > 1000, 'audio contains speech rather than silence');
      assert.ok(slow.length / normal.length > 1.3 && slow.length / normal.length < 1.6, '0.7x generates proportionally longer audio');
    }
    await assert.rejects(synthesizeOffline('こんにちは。', 'ja-JP-UnknownNeural', 1), /Unknown offline voice/);
  } finally { globalThis.fetch = originalFetch; closeOfflineSpeech(); }
});
