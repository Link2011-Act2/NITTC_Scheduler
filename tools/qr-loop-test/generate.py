"""Generate a standalone offline test page using the app's cached ZXing dependency."""
import base64
import gzip
import hashlib
import json
import os
from pathlib import Path
import random
import string
import subprocess
import tempfile
import uuid

ROOT = Path(__file__).resolve().parent
JAVA = Path(r"C:\Program Files\Android\Android Studio1\jbr\bin\java.exe")
CACHE = Path.home() / ".gradle/caches/modules-2/files-2.1/com.google.zxing/core/3.5.4"
ZXING = next(p for p in CACHE.rglob("core-3.5.4.jar"))
randomizer = random.Random(20261003)
noise = "".join(randomizer.choices(string.ascii_letters + string.digits, k=60000))


def encode(length, count):
    middle = length // 2
    payload = {
        "format": "SKTTP/QR", "version": 1, "year": 2026,
        "label": f"{count}枚受信テスト", "created": 1790956800000,
        "semesterStart": [10, 1], "sections": ["NOTES"],
        "notes": [
            ["2026-10-03", 0, "QR受信テスト（保存不要）\n" + noise[:middle]],
            ["2026-10-03", 1, "QR受信テスト（保存不要）\n" + noise[middle:length]],
        ],
    }
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return raw, gzip.compress(raw, compresslevel=6, mtime=0)


def compressed_payload(count):
    target = count * 200
    low, high = 1, 60000
    while low < high:
        middle = (low + high) // 2
        if len(encode(middle, count)[1]) < target:
            low = middle + 1
        else:
            high = middle
    for length in range(max(1, low - 40), min(60000, low + 40) + 1):
        raw, compressed = encode(length, count)
        if len(compressed) == target:
            break
    else:
        # Legal GZIP extra header padding; ordinary GZIPInputStream skips this field.
        raw, compressed = encode(max(1, low - 100), count)
        extra_size = target - len(compressed) - 2
        assert 0 <= extra_size <= 65535 and compressed[3] == 0
        compressed = (compressed[:3] + b"\x04" + compressed[4:10] +
                      extra_size.to_bytes(2, "little") + bytes(extra_size) + compressed[10:])
    assert len(compressed) == target and gzip.decompress(compressed) == raw
    assert all(len(row[2]) <= 32000 for row in json.loads(raw)["notes"])
    return raw, compressed


datasets = {}
frames = []
for count in (16, 32, 64, 128):
    raw, compressed = compressed_payload(count)
    digest = hashlib.sha256(compressed).hexdigest()
    datasets[str(count)] = {}
    for mode in ("normal", "hash", "duplicate"):
        transfer_id = uuid.uuid4().hex
        advertised_digest = ("0" if digest[0] != "0" else "1") + digest[1:] if mode == "hash" else digest
        def frame(index, content):
            return f"SKTTP/QR:1:{transfer_id}:{advertised_digest}:{count}:{index}:" + base64.b64encode(content).decode("ascii")
        sequence = [frame(i, compressed[i * 200:(i + 1) * 200]) for i in range(count)]
        if mode == "duplicate":
            changed = bytearray(compressed[:200])
            changed[-1] ^= 1
            # Repeat #1 with different bytes before the other fragments.
            sequence.insert(1, frame(0, changed))
        assert all(len(base64.b64decode(f.split(":", 6)[6])) == 200 for f in sequence)
        datasets[str(count)][mode] = {"frames": sequence, "transferId": transfer_id,
            "digest": advertised_digest, "jsonBytes": len(raw), "compressedBytes": len(compressed)}
        frames.extend(sequence)
frame_path = ROOT / "frames.txt"
image_path = ROOT / "images.json"
frame_path.write_text("\n".join(frames), encoding="utf-8")
try:
    with tempfile.TemporaryDirectory(prefix="skttp-qr-test-") as compiled:
        subprocess.run([str(JAVA.with_name("javac.exe")), "-encoding", "UTF-8", "-classpath", str(ZXING),
                        "-d", compiled, str(ROOT / "QrTestImages.java")], check=True)
        subprocess.run([str(JAVA), "-classpath", compiled + os.pathsep + str(ZXING), "QrTestImages",
                        str(frame_path), str(image_path)], check=True)
    images = json.loads(image_path.read_text(encoding="utf-8"))
finally:
    frame_path.unlink(missing_ok=True)
    image_path.unlink(missing_ok=True)

offset = 0
for modes in datasets.values():
    for dataset in modes.values():
        size = len(dataset["frames"])
        dataset["images"] = images[offset:offset + size]
        offset += size
assert offset == len(images)
html = (ROOT / "template.html").read_text(encoding="utf-8")
html = html.replace("/*__TEST_DATA__*/", json.dumps({"datasets": datasets}, ensure_ascii=False))
(ROOT / "index.html").write_text(html, encoding="utf-8")
print(json.dumps({"page": str(ROOT / "index.html"), "counts": [16, 32, 64, 128],
                  "bytesPerPart": 200, "modes": ["normal", "hash", "duplicate"],
                  "qrRoundTrips": len(images)}, ensure_ascii=False))
