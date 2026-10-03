// Verify payloads and run the actual page script against a deterministic animation clock.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const zlib = require('node:zlib');
const script = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
const data = JSON.parse(script.match(/^  const data = (.*);$/m)[1]);
const ids = new Set();
function verifyDataset(dataset, count, mode, bytesPerPart) {
    assert.ok(!ids.has(dataset.transferId)); ids.add(dataset.transferId);
    assert.equal(dataset.frames.length, count + (mode === 'duplicate' ? 1 : 0));
    assert.equal(dataset.images.length, dataset.frames.length);
    const parts = dataset.frames.map((frame, position) => {
      const fields = frame.split(':');
      const index = mode === 'duplicate' && position > 0 ? position - 1 : position;
      assert.deepEqual(fields.slice(0, 6), ['SKTTP/QR', '1', dataset.transferId, dataset.digest, String(count), String(index)]);
      const bytes = Buffer.from(fields[6], 'base64'); assert.equal(bytes.length, bytesPerPart);
      return bytes;
    });
    if (mode === 'duplicate') {
      assert.notDeepEqual(parts[0], parts[1]);
      const differences = parts[0].reduce((n, value, i) => n + (value !== parts[1][i] ? 1 : 0), 0);
      assert.equal(differences, 1); parts.splice(1, 1);
    }
    const compressed = Buffer.concat(parts);
    assert.equal(compressed.length, count * bytesPerPart);
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
for (const count of [16, 32, 64, 128]) {
  for (const mode of ['normal', 'hash', 'duplicate']) verifyDataset(data.datasets[count][mode], count, mode, 200);
}

function element(value = '') {
  return { value, min: '1', max: '128', hidden: false, textContent: '', src: '', alt: '', events: {},
    addEventListener(event, callback) { this.events[event] = callback; },
    reportValidity() { return this.value !== '' && Number.isInteger(Number(this.value)) && Number(this.value) >= Number(this.min) && Number(this.value) <= Number(this.max); } };
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
  crypto: crypto.webcrypto, atob, btoa, setTimeout,
  Image: class { decode() { return Promise.resolve(); } },
  requestAnimationFrame(callback) { frameCallback = callback; } });
vm.runInContext(script, context);
function step(ms) { now += ms; frameCallback(now); }
function click(id) { elements.get(id).events.click(); }
function shown() { return Number(elements.get('#index').textContent); }
function mode(value) { elements.get('#mode').events.change({ target: { value } }); }
async function settle() { await new Promise(resolve => setImmediate(resolve)); }
async function prepared() {
  for (let i = 0; i < 2000; i++) {
    if (vm.runInContext('ready', context)) return;
    await new Promise(resolve => setTimeout(resolve, 10));
  }
  throw new Error('QR generation did not complete');
}
function applyBytes(value) {
  elements.get('#bytes').value = String(value);
  elements.get('#bytes-form').events.submit({ preventDefault() {} });
}
(async () => {
  await prepared();
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
    await prepared();
    assert.equal(shown(), 1); assert.equal(Number(elements.get('#total').textContent), count);
    assert.equal(elements.get('#start').max, String(count));
    for (let i = 1; i <= count; i++) { step(50); assert.equal(shown(), i % count + 1); }
  }
  counts[0].events.change();
  await prepared();
  document.querySelector('#start').value = '128'; click('#jump'); assert.equal(shown(), 1);
  document.querySelector('#start').value = ''; click('#jump'); assert.equal(shown(), 1);
  mode('hash'); await prepared(); assert.equal(shown(), 1); assert.match(elements.get('#mode-help').textContent, /SHA-256/);
  assert.equal(elements.get('#fault').hidden, true);
  mode('duplicate'); await prepared(); assert.equal(elements.get('#fault').hidden, false);
  step(50); assert.equal(shown(), 1); assert.match(elements.get('#status').textContent, /内容改変/);
  step(50); assert.equal(shown(), 2);
  click('#fault'); assert.equal(shown(), 1); assert.equal(elements.get('#pause').textContent, '再開');
  assert.match(elements.get('#status').textContent, /内容改変/);
  step(1000); assert.equal(shown(), 1);
  mode('normal'); await prepared(); assert.equal(shown(), 1); assert.equal(elements.get('#pause').textContent, '再開');
  click('#pause'); speeds[0].events.change(); step(199); assert.equal(shown(), 1); step(1); assert.equal(shown(), 2);
  speeds[2].events.change(); step(64); assert.equal(shown(), 2); step(1); assert.equal(shown(), 3);
  click('#restart'); assert.equal(shown(), 1);
  document.querySelector('#bytes').min = '100'; document.querySelector('#bytes').max = '1600';
  const originalId = vm.runInContext('dataset().transferId', context);
  for (const invalid of ['', 99, 1601, 200.5]) {
    applyBytes(invalid); await settle();
    assert.equal(vm.runInContext('dataset().transferId', context), originalId);
  }
  applyBytes(333); await prepared();
  verifyDataset(vm.runInContext('dataset()', context), 16, 'normal', 333);
  assert.match(elements.get('#badge').textContent, /333 bytes/);
  assert.match(elements.get('#metadata').textContent, /333B/);
  click('#pause');
  applyBytes(1600); await prepared();
  assert.equal(elements.get('#pause').textContent, '再開');
  verifyDataset(vm.runInContext('dataset()', context), 16, 'normal', 1600);
  counts[3].events.change(); await prepared();
  assert.equal(elements.get('#bytes').value, '1600');
  assert.equal(elements.get('#bytes').max, '1600');
  verifyDataset(vm.runInContext('dataset()', context), 128, 'normal', 1600);
  assert.equal(elements.get('#capacity-warning').hidden, false);
  assert.match(elements.get('#capacity-warning').textContent, /204,800B/);
  applyBytes(512); await prepared(); assert.equal(elements.get('#capacity-warning').hidden, true);
  applyBytes(513); await prepared(); assert.equal(elements.get('#capacity-warning').hidden, false);
  assert.equal(elements.get('#bytes').value, '513');
  // A newer request must win while a larger generation is still yielding.
  applyBytes(400); applyBytes(201); mode('hash'); mode('duplicate'); await prepared();
  verifyDataset(vm.runInContext('dataset()', context), 128, 'duplicate', 201);
  assert.match(elements.get('#metadata').textContent, /201B/);
  click('#fault'); assert.match(elements.get('#status').textContent, /内容改変/);
  mode('normal'); applyBytes(200); await prepared();
  assert.equal(elements.get('#capacity-warning').hidden, true);
  assert.equal(vm.runInContext('dataset().transferId', context), data.datasets[128].normal.transferId);

  const cases = [], symbols = [];
  const settings = [[16, 100], [16, 333], [16, 1600], [32, 999], [64, 1000], [128, 512], [128, 513], [128, 1600]];
  for (const [count, bytesPerPart] of settings) {
    for (const mode of ['normal', 'hash', 'duplicate']) {
      const dataset = await context.qrTestDataset(data.basePayload, count, bytesPerPart, mode);
      verifyDataset(dataset, count, mode, bytesPerPart);
      cases.push(`${count * bytesPerPart > 65536 && mode !== 'duplicate' ? 'oversized' : mode} ${count} ${dataset.frames.length}`, ...dataset.frames);
      for (const frame of [dataset.frames[0], dataset.frames.at(-1)]) {
        const symbol = context.qrTestSymbol(frame);
        assert.equal(symbol.errorCorrectionLevel, context.qrcodegen.QrCode.Ecc.MEDIUM);
        symbols.push(`${Buffer.from(frame).toString('base64')} ${symbol.size}`);
        for (let y = 0; y < symbol.size; y++) symbols.push(Array.from({ length: symbol.size }, (_, x) => symbol.getModule(x, y) ? '1' : '0').join(''));
      }
    }
  }
  for (const invalid of [99, 1601, 100.5]) {
    await assert.rejects(context.qrTestDataset(data.basePayload, 128, invalid, 'normal'));
  }
  const output = path.join(__dirname, 'out'); fs.mkdirSync(output, { recursive: true });
  fs.writeFileSync(path.join(output, 'dynamic-cases.txt'), cases.join('\n'));
  fs.writeFileSync(path.join(output, 'dynamic-symbols.txt'), symbols.join('\n'));
  console.log('PASS: 12 prebuilt + 24 dynamic datasets; exact 100–1600B fragments, valid GZIP/JSON, error scenarios, loops, speeds, editing, warning-only capacity limit, pause, generation races.');
})().catch(error => { console.error(error); process.exitCode = 1; });
