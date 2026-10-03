// Verify generated payload and run the real page script with a deterministic animation clock.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const zlib = require('node:zlib');
const html = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
const data = JSON.parse(script.match(/const data = (.*);/)[1]);
assert.equal(data.images.length, 128);
assert.equal(data.frames.length, 128);
const parts = data.frames.map((frame, index) => {
  const fields = frame.split(':');
  assert.deepEqual(fields.slice(0, 6), ['SKTTP/QR', '1', data.transferId, data.digest, '128', String(index)]);
  const bytes = Buffer.from(fields[6], 'base64');
  assert.equal(bytes.length, 200);
  return bytes;
});
const compressed = Buffer.concat(parts);
assert.equal(compressed.length, 25600);
assert.equal(crypto.createHash('sha256').update(compressed).digest('hex'), data.digest);
const payload = JSON.parse(zlib.gunzipSync(compressed));
assert.equal(payload.format, 'SKTTP/QR');
assert.equal(payload.version, 1);
assert.deepEqual(payload.sections, ['NOTES']);
assert.equal(payload.notes.length, 2);
assert.equal(new Set(payload.notes.map(row => `${row[0]}/${row[1]}`)).size, 2);
for (const row of payload.notes) {
  assert.equal(row.length, 3);
  assert.ok(row[1] >= 0 && row[1] <= 11);
  assert.ok(typeof row[2] === 'string' && row[2].length <= 32000);
}

function element(value = '') {
  return { value, textContent: '', src: '', alt: '', events: {},
    addEventListener(event, callback) { this.events[event] = callback; },
    reportValidity() { return Number(this.value) >= 1 && Number(this.value) <= 128; } };
}
const elements = new Map();
const radio = [200, 125, 65, 50].map(value => element(String(value)));
const document = { hidden: false, events: {},
  querySelector(selector) { if (!elements.has(selector)) elements.set(selector, element()); return elements.get(selector); },
  querySelectorAll() { return radio; },
  addEventListener(event, callback) { this.events[event] = callback; } };
let now = 0, frameCallback;
const context = vm.createContext({ document, performance: { now: () => now },
  Image: class { decode() { return Promise.resolve(); } },
  requestAnimationFrame(callback) { frameCallback = callback; } });
vm.runInContext(script, context);
function step(ms) { now += ms; frameCallback(now); }
function click(id) { elements.get(id).events.click(); }
function shown() { return Number(elements.get('#index').textContent); }
(async () => {
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(shown(), 1);
  for (let i = 1; i <= 128; i++) { step(125); assert.equal(shown(), i % 128 + 1); }
  click('#pause'); step(1000); assert.equal(shown(), 1);
  document.querySelector('#start').value = '128'; click('#jump'); assert.equal(shown(), 128);
  radio[3].events.change(); assert.equal(shown(), 128);
  assert.match(elements.get('#timing').textContent, /50ms/);
  click('#pause'); step(50); assert.equal(shown(), 1);
  step(1000); assert.equal(shown(), 2); // A late frame advances one fragment, never skips.
  document.hidden = true; step(5000); assert.equal(shown(), 2);
  document.hidden = false; document.events.visibilitychange(); step(50); assert.equal(shown(), 3);
  click('#restart'); assert.equal(shown(), 1);
  radio[0].events.change(); step(199); assert.equal(shown(), 1); step(1); assert.equal(shown(), 2);
  radio[2].events.change(); step(64); assert.equal(shown(), 2); step(1); assert.equal(shown(), 3);
  elements.get('#start').value = '0'; click('#jump'); assert.equal(shown(), 3);
  console.log('PASS: 128 × 200B, SHA-256, GZIP, JSON, loop, 4 speeds, pause, jump, dropped frames, hidden tab.');
})().catch(error => { console.error(error); process.exitCode = 1; });
