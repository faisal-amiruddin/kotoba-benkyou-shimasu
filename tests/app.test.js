import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../public/app.js', import.meta.url), 'utf8');
function loadFunction(name, next) {
  const start = source.indexOf(`function ${name}(`);
  return vm.runInNewContext(`(${source.slice(start, source.indexOf(next, start)).trim()})`, { Blob });
}
const parse = loadFunction('parseDialogue', 'function renderDialogue');
const mergeWav = loadFunction('mergeWav', 'function setStatus');
const script = '{cowo[1]} こんにちは。\n{translate[1]} Halo.\n{romaji[1]} Konnichiwa.\n{cewe[2]} はい。\n{romaji[2]} Hai.\n{translate[2]} Ya.';

test('prompt uses one conversation with two speakers and 540 turns for N5 / 45 minutes', () => {
  const scope = { levelEl: { value: 'N5' }, durationEl: { value: '45' }, topicEl: { value: '' }, promptOutput: { value: '' }, levelGuides: { N5: 'Kosakata dasar N5.' } };
  const start = source.indexOf('function buildPrompt(');
  const body = source.slice(start, source.indexOf('[levelEl, durationEl, topicEl]', start)).trim();
  vm.runInNewContext(`(${body})`, scope)();
  const prompt = scope.promptOutput.value;
  assert.match(prompt, /^Buat naskah percakapan bahasa Jepang untuk latihan listening dua orang: satu cowok dan satu cewek\./);
  assert.match(prompt, /LEVEL: JLPT N5/);
  assert.match(prompt, /540 giliran bicara\/baris/);
  assert.match(prompt, /\{cowo\[1\]\} こんにちは。/);
  assert.match(prompt, /\{translate\[2\]\} Hari ini mau melakukan apa\?/);
  assert.ok(!prompt.includes('PEMBAGIAN TOPIK'));
  assert.ok(!prompt.includes('{narator['));
});

test('pairs annotations by ID even when translation precedes romaji; only Japanese is spoken', () => {
  const result = parse(script);
  assert.equal(result.invalid.length, 0);
  assert.equal(result.parsed.length, 2);
  assert.equal(result.parsed[0].text, 'こんにちは。');
  assert.equal(result.parsed[0].translation, 'Halo.');
  assert.equal(result.parsed[1].romaji, 'Hai.');
});
test('rejects missing, duplicate, orphaned, and out-of-sequence annotations', () => {
  for (const raw of [script.replace('{romaji[1]} Konnichiwa.\n', ''), script + '\n{translate[1]} Duplikat', script + '\n{translate[3]} Salah', script.replaceAll('[2]', '[3]'), script + '\n{cowo[2]} 重複', '{cowo[0]} 不正']) {
    assert.ok(parse(raw).invalid.length > 0, raw);
  }
});
test('legacy dialogue remains usable and mixed numbering is rejected', () => {
  assert.equal(parse('{cowo} こんにちは。\n[cewe] はい。\ncowo: そうです。').invalid.length, 0);
  assert.ok(parse(script + '\n{cewe} はい。').invalid.length);
  assert.equal(parse('```text\n' + script + '\n```').invalid.length, 0);
});
test('narrator scripts are rejected after removing narrator support', () => {
  const raw = '{narator[1]} 駅で話します。\n{romaji[1]} Eki de hanashimasu.\n{translate[1]} Berbicara di stasiun.\n{cowo[2]} はい。\n{romaji[2]} Hai.\n{translate[2]} Ya.\n{narator[3]} 次は学校です。\n{romaji[3]} Tsugi wa gakkou desu.\n{translate[3]} Berikutnya di sekolah.';
  const result = parse(raw);
  assert.ok(result.invalid.length > 0);
  assert.ok(result.parsed.every(line => line.speaker !== 'narator'));
  assert.equal(parse('{narator} 学校です。').invalid.length, 1);
});
test('WAV joins sentence samples without changing exact duration or boundaries', async () => {
  const first = new Int16Array([100, -100, 200]);
  const second = new Int16Array([-200, 300]);
  const wav = mergeWav([new Uint8Array(first.buffer), new Uint8Array(second.buffer)], 24000);
  const bytes = await wav.arrayBuffer();
  const view = new DataView(bytes);
  assert.equal(wav.type, 'audio/wav');
  assert.equal(view.getUint32(24, true), 24000);
  assert.equal(view.getUint32(40, true), 10);
  assert.equal(bytes.byteLength, 54);
  assert.deepEqual([...new Int16Array(bytes, 44)], [100, -100, 200, -200, 300]);
});
