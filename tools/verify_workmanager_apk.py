"""Verify WorkManager's reflective constructors in the final release APK.

Usage: python tools/verify_workmanager_apk.py app-release-unsigned.apk \
    --dexdump <Android SDK>/build-tools/<version>/dexdump.exe
"""

import argparse
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


INPUT_MERGERS = (
    "androidx.work.OverwritingInputMerger",
    "androidx.work.ArrayCreatingInputMerger",
)


def verify(apk: Path, dexdump: Path) -> None:
    found = set()
    with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory() as directory:
        for entry in archive.namelist():
            if not re.fullmatch(r"classes\d*\.dex", entry):
                continue
            dex = Path(directory) / entry
            dex.write_bytes(archive.read(entry))
            result = subprocess.run(
                [str(dexdump), str(dex)], check=True, capture_output=True,
                encoding="utf-8", errors="replace",
            )
            for block in re.split(r"(?m)^Class #\d+", result.stdout):
                descriptor = re.search(r"Class descriptor\s*:\s*'L([^;]+);'", block)
                if descriptor is None:
                    continue
                name = descriptor.group(1).replace("/", ".")
                if name not in INPUT_MERGERS:
                    continue
                # Check the actual DEX method and access flags, not the source/keep rule.
                constructor = re.search(
                    r"name\s*:\s*'<init>'\s+type\s*:\s*'\(\)V'\s+"
                    r"access\s*:\s*0x([0-9a-fA-F]+)", block,
                )
                if constructor is None or not (int(constructor.group(1), 16) & 0x1):
                    raise ValueError(f"{name}: public no-argument constructor missing in {entry}")
                found.add(name)
                print(f"OK: {name} public <init>() in {entry}")
    missing = set(INPUT_MERGERS) - found
    if missing:
        raise ValueError(f"InputMerger classes missing from APK: {', '.join(sorted(missing))}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--dexdump", type=Path, required=True)
    args = parser.parse_args()
    try:
        verify(args.apk.resolve(), args.dexdump.resolve())
    except (OSError, ValueError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        parser.exit(1, f"FAIL: {error}\n")
