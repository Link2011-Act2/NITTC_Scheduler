// Verify payloads and run the actual page script against a deterministic animation clock.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const zlib = require('node:zlib');
const script = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
const data = JSON.parse(script.match(/const data = (.*);/)[1]);
const ids = new Set();
for (const count of [16, 32, 64, 128]) {
  for (const mode of ['normal', 'hash', 'duplicate']) {
    const dataset = data.datasets[count][mode];
    assert.ok(!ids.has(dataset.transferId)); ids.add(dataset.transferId);
    assert.equal(dataset.frames.length, count + (mode === 'duplicate' ? 1 : 0));
    assert.equal(dataset.images.length, dataset.frames.length);
    const parts = dataset.frames.map((frame, position) => {
      const fields = frame.split(':');
      const index = mode === 'duplicate' && position > 0 ? position - 1 : position;
      assert.deepEqual(fields.slice(0, 6), ['SKTTP/QR', '1', dataset.transferId, dataset.digest, String(count), String(index)]);
      const bytes = Buffer.from(fields[6], 'base64'); assert.equal(bytes.length, 200);
      return bytes;
    });
    if (mode === 'duplicate') {
      assert.notDeepEqual(parts[0], parts[1]);
      const differences = parts[0].reduce((n, value, i) => n + (value !== parts[1][i] ? 1 : 0), 0);
      assert.equal(differences, 1); parts.splice(1, 1);
    }
    const compressed = Buffer.concat(parts);
    assert.equal(compressed.length, count * 200);
    const hash = crypto.createHash('sha256').update(compressed).digest('hex');
    if (mode === 'hash') assert.notEqual(hash, dataset.digest);
    else assert.equal(hash, dataset.digest);
    const raw = zlib.gunzipSync(compressed);
    assert.equal(raw.length, dataset.jsonBytes);
    const payload = JSON.parse(raw);
    assert.equal(payload.format, 'SKTTP/QR'); assert.equal(payload.version, 1);
    assert.deepEqual(payload.sections, ['NOTES']); assert.equal(payload.notes.length, 2);
    assert.equal(new Set(payload.notes.map(row => `${row[0]}/${row[1]}`)).size, 2);
    for (const row of payload.notes) {
      assert.equal(row.length, 3); assert.ok(row[1] >= 0 && row[1] <= 11);
      assert.ok(typeof row[2] === 'string' && row[2].length <= 32000);
    }
  }
}

function element(value = '') {
  return { value, max: '128', hidden: false, textContent: '', src: '', alt: '', events: {},
    addEventListener(event, callback) { this.events[event] = callback; },
    reportValidity() { return Number(this.value) >= 1 && Number(this.value) <= Number(this.max); } };
}
const elements = new Map();
const speeds = [200, 125, 65, 50].map(value => element(String(value)));
const counts = [16, 32, 64, 128].map(value => element(String(value)));
const document = { hidden: false, events: {},
  querySelector(selector) { if (!elements.has(selector)) elements.set(selector, element()); return elements.get(selector); },
  querySelectorAll(selector) { return selector.includes('speed') ? speeds : counts; },
  addEventListener(event, callback) { this.events[event] = callback; } };
let now = 0, frameCallback;
const context = vm.createContext({ document, performance: { now: () => now },
  Image: class { decode() { return Promise.resolve(); } },
  requestAnimationFrame(callback) { frameCallback = callback; } });
vm.runInContext(script, context);
function step(ms) { now += ms; frameCallback(now); }
function click(id) { elements.get(id).events.click(); }
function shown() { return Number(elements.get('#index').textContent); }
function mode(value) { elements.get('#mode').events.change({ target: { value } }); }
(async () => {
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(shown(), 1);
  for (let i = 1; i <= 128; i++) { step(125); assert.equal(shown(), i % 128 + 1); }
  click('#pause'); step(1000); assert.equal(shown(), 1);
  document.querySelector('#start').value = '128'; click('#jump'); assert.equal(shown(), 128);
  speeds[3].events.change(); assert.equal(shown(), 128);
  assert.match(elements.get('#timing').textContent, /50ms/);
  click('#pause'); step(50); assert.equal(shown(), 1);
  step(1000); assert.equal(shown(), 2);
  document.hidden = true; step(5000); assert.equal(shown(), 2);
  document.hidden = false; document.events.visibilitychange(); step(50); assert.equal(shown(), 3);
  for (let n = 0; n < counts.length; n++) {
    counts[n].events.change(); const count = Number(counts[n].value);
    assert.equal(shown(), 1); assert.equal(Number(elements.get('#total').textContent), count);
    assert.equal(elements.get('#start').max, String(count));
    for (let i = 1; i <= count; i++) { step(50); assert.equal(shown(), i % count + 1); }
  }
  counts[0].events.change();
  document.querySelector('#start').value = '128'; click('#jump'); assert.equal(shown(), 1);
  document.querySelector('#start').value = ''; click('#jump'); assert.equal(shown(), 1);
  mode('hash'); assert.equal(shown(), 1); assert.match(elements.get('#mode-help').textContent, /SHA-256/);
  assert.equal(elements.get('#fault').hidden, true);
  mode('duplicate'); assert.equal(elements.get('#fault').hidden, false);
  step(50); assert.equal(shown(), 1); assert.match(elements.get('#status').textContent, /内容改変/);
  step(50); assert.equal(shown(), 2);
  click('#fault'); assert.equal(shown(), 1); assert.equal(elements.get('#pause').textContent, '再開');
  assert.match(elements.get('#status').textContent, /内容改変/);
  step(1000); assert.equal(shown(), 1);
  mode('normal'); assert.equal(shown(), 1); assert.equal(elements.get('#pause').textContent, '再開');
  click('#pause'); speeds[0].events.change(); step(199); assert.equal(shown(), 1); step(1); assert.equal(shown(), 2);
  speeds[2].events.change(); step(64); assert.equal(shown(), 2); step(1); assert.equal(shown(), 3);
  click('#restart'); assert.equal(shown(), 1);
  console.log('PASS: all 12 datasets, 200B fragments, expected hash/duplicate errors, valid JSON, 4 counts, loops, 4 speeds, pause, jump, visibility, bounds.');
})().catch(error => { console.error(error); process.exitCode = 1; });
