// Offline test data only: GZIP FEXTRA padding changes QR density without adding huge notes.
function qrTestCompressed(basePayload, targetBytes) {
  const base = Uint8Array.from(atob(basePayload.gzip), char => char.charCodeAt(0));
  // Empty concatenated GZIP members allow testing beyond the 64KiB receiver limit.
  // Each member has at most 65535 extra bytes; decompression still yields only the original JSON.
  const empty = new Uint8Array([31, 139, 8, 0, 0, 0, 0, 0, 0, 3, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
  if (base[3] !== 0 || !Number.isInteger(targetBytes) || targetBytes < base.length + 2 || targetBytes > 128 * 1600) throw new Error('Invalid test size');
  const compressed = new Uint8Array(targetBytes);
  let offset = 0, member = base;
  while (offset < targetBytes) {
    const remaining = targetBytes - offset;
    let size = Math.min(remaining, member.length + 2 + 65535);
    // Leave enough space for another empty member's header, XLEN, and footer.
    if (remaining > size && remaining - size < empty.length + 2) size -= empty.length + 2 - (remaining - size);
    const padding = size - member.length - 2;
    compressed.set(member.subarray(0, 10), offset);
    compressed[offset + 3] = 4;
    compressed[offset + 10] = padding & 255; compressed[offset + 11] = padding >>> 8;
    // Random bytes represent compressed data rather than all-zero QR payloads.
    crypto.getRandomValues(compressed.subarray(offset + 12, offset + 12 + padding));
    compressed.set(member.subarray(10), offset + 12 + padding);
    offset += size; member = empty;
  }
  return compressed;
}

function qrTestSymbol(frame) {
  return qrcodegen.QrCode.encodeSegments(
    [qrcodegen.QrSegment.makeBytes(Array.from(frame, char => char.charCodeAt(0)))],
    qrcodegen.QrCode.Ecc.MEDIUM, 1, 40, -1, false);
}

function qrTestImage(frame) {
  const symbol = qrTestSymbol(frame), size = symbol.size + 8, paths = [];
  for (let y = 0; y < symbol.size; y++) {
    for (let x = 0; x < symbol.size; x++) {
      if (symbol.getModule(x, y)) paths.push(`M${x + 4},${y + 4}h1v1h-1z`);
    }
  }
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" shape-rendering="crispEdges"><rect width="100%" height="100%" fill="white"/><path d="${paths.join('')}" fill="black"/></svg>`;
  return 'data:image/svg+xml;base64,' + btoa(svg);
}

async function qrTestDataset(basePayload, count, bytesPerPart, mode, progress = () => {}) {
  if (![16, 32, 64, 128].includes(count) || !Number.isInteger(bytesPerPart) ||
      bytesPerPart < 100 || bytesPerPart > 1600 ||
      !['normal', 'hash', 'duplicate'].includes(mode)) throw new Error('Invalid test settings');
  const compressed = qrTestCompressed(basePayload, count * bytesPerPart);
  const digestBytes = new Uint8Array(await crypto.subtle.digest('SHA-256', compressed));
  let digest = Array.from(digestBytes, value => value.toString(16).padStart(2, '0')).join('');
  if (mode === 'hash') digest = (digest[0] === '0' ? '1' : '0') + digest.slice(1);
  const transferId = Array.from(crypto.getRandomValues(new Uint8Array(16)), value => value.toString(16).padStart(2, '0')).join('');
  function frame(index, bytes) {
    return `SKTTP/QR:1:${transferId}:${digest}:${count}:${index}:` + btoa(String.fromCharCode(...bytes));
  }
  const frames = Array.from({ length: count }, (_, index) =>
    frame(index, compressed.subarray(index * bytesPerPart, (index + 1) * bytesPerPart)));
  if (mode === 'duplicate') {
    const changed = compressed.slice(0, bytesPerPart); changed[changed.length - 1] ^= 1;
    frames.splice(1, 0, frame(0, changed));
  }
  const images = [];
  for (const text of frames) {
    // Stop obsolete generation when the user changes settings again.
    if (progress(images.length, frames.length) === false) return null;
    images.push(qrTestImage(text));
    if (images.length % 4 === 0) await new Promise(resolve => setTimeout(resolve, 0));
  }
  return { frames, images, transferId, digest, jsonBytes: basePayload.jsonBytes, compressedBytes: compressed.length };
}
