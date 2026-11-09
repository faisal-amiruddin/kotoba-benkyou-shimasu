import test from 'node:test';
import assert from 'node:assert/strict';
import { voicevoxConfigured, getVoicevoxVoices, synthesizeVoicevox, closeVoicevox } from '../lib/voicevox.js';

test('VOICEVOX CPU synthesizes both speakers, applies tempo/intonation/styles, and recovers after invalid style', {skip: !voicevoxConfigured, timeout: 60000}, async () => {
  const previous = globalThis.fetch;
  globalThis.fetch = () => { throw new Error('Offline test forbids Node network requests'); };
  try {
    const voices = await getVoicevoxVoices();
    assert.ok(voices.some(v => v.ShortName === 'voicevox-11' && v.Gender === 'Male'));
    assert.ok(voices.some(v => v.ShortName === 'voicevox-2' && v.Gender === 'Female'));
    assert.ok(voices.every(v => v.Offline && v.Credit.startsWith('VOICEVOX:')));
    const text = 'こんにちは。元気ですか？';
    const [male, female] = await Promise.all([synthesizeVoicevox(text, 'voicevox-11'), synthesizeVoicevox(text, 'voicevox-2')]);
    for (const wav of [male, female]) {
      assert.equal(wav.toString('ascii', 0, 4), 'RIFF');
      assert.equal(wav.toString('ascii', 8, 12), 'WAVE');
      assert.equal(wav.readUInt32LE(24), 24000);
      assert.equal(wav.readUInt16LE(22), 1);
      let peak = 0;
      for (let i = 44; i < wav.length; i += 2) peak = Math.max(peak, Math.abs(wav.readInt16LE(i)));
      assert.ok(peak > 1000, 'WAV contains speech');
    }
    assert.notDeepEqual(male, female);
    const slow = await synthesizeVoicevox(text, 'voicevox-11', 0.7);
    assert.ok(slow.length > male.length * 1.25 && slow.length < male.length * 1.6);
    const flat = await synthesizeVoicevox(text, 'voicevox-2', 1, 0);
    assert.notDeepEqual(flat, female, 'intonation changes the generated waveform');
    const sweet = await synthesizeVoicevox(text, 'voicevox-0');
    assert.notDeepEqual(sweet, female, 'selected speaking style changes speech');
    await assert.rejects(synthesizeVoicevox(text, 'voicevox-9999'), /not installed/);
    assert.equal((await getVoicevoxVoices()).length, voices.length);
  } finally { globalThis.fetch = previous; closeVoicevox(); }
});
