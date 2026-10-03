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
TARGET = 128 * 200
randomizer = random.Random(20261003)
noise = "".join(randomizer.choices(string.ascii_letters + string.digits, k=60000))


def encode(length):
    middle = length // 2
    payload = {
        "format": "SKTTP/QR", "version": 1, "year": 2026,
        "label": "128枚受信テスト", "created": 1790956800000,
        "semesterStart": [10, 1], "sections": ["NOTES"],
        "notes": [
            ["2026-10-03", 0, "QR受信テスト（保存不要）\n" + noise[:middle]],
            ["2026-10-03", 1, "QR受信テスト（保存不要）\n" + noise[middle:length]],
        ],
    }
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return raw, gzip.compress(raw, compresslevel=6, mtime=0)


low, high = 1, 60000
while low < high:
    middle = (low + high) // 2
    if len(encode(middle)[1]) < TARGET:
        low = middle + 1
    else:
        high = middle

for length in range(max(1, low - 40), min(60000, low + 40) + 1):
    raw, compressed = encode(length)
    if len(compressed) == TARGET:
        break
else:
    # Legal GZIP extra header padding; ordinary GZIPInputStream skips this field.
    raw, compressed = encode(max(1, low - 100))
    extra_size = TARGET - len(compressed) - 2
    assert 0 <= extra_size <= 65535 and compressed[3] == 0
    compressed = (compressed[:3] + b"\x04" + compressed[4:10] +
                  extra_size.to_bytes(2, "little") + bytes(extra_size) + compressed[10:])

assert len(compressed) == TARGET and gzip.decompress(compressed) == raw
payload = json.loads(raw)
assert all(len(row[2]) <= 32000 for row in payload["notes"])
digest = hashlib.sha256(compressed).hexdigest()
transfer_id = uuid.uuid4().hex
frames = [
    f"SKTTP/QR:1:{transfer_id}:{digest}:128:{i}:" +
    base64.b64encode(compressed[i * 200:(i + 1) * 200]).decode("ascii")
    for i in range(128)
]
assert all(len(base64.b64decode(f.split(":", 6)[6])) == 200 for f in frames)
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

html = (ROOT / "template.html").read_text(encoding="utf-8")
html = html.replace("/*__TEST_DATA__*/", json.dumps({
    "images": images, "frames": frames, "transferId": transfer_id,
    "digest": digest, "jsonBytes": len(raw), "compressedBytes": len(compressed),
}, ensure_ascii=False))
(ROOT / "index.html").write_text(html, encoding="utf-8")
print(json.dumps({"page": str(ROOT / "index.html"), "parts": len(frames),
                  "bytesPerPart": 200, "compressedBytes": len(compressed),
                  "jsonBytes": len(raw), "qrRoundTrips": len(images)}, ensure_ascii=False))
