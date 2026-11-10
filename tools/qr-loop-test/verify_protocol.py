"""Verify generated scenarios using the Collector compiled by :app:compileDebugKotlin."""
import json
import os
from pathlib import Path
import re
import subprocess
import shutil
import tempfile

root = Path(__file__).resolve().parent
repo = root.parent.parent
classes = repo / "app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
assert (classes / "jp/linkserver/nittcsc/logic/QrShareCollector.class").exists(), "Run :app:compileDebugKotlin first"
stdlib = next((Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.3.20").rglob("kotlin-stdlib-2.3.20.jar"))
jdk = os.environ.get("JAVA_HOME")
java_name = "java.exe" if os.name == "nt" else "java"
java_path = str(Path(jdk) / "bin" / java_name) if jdk else shutil.which("java")
if not java_path or not Path(java_path).is_file():
    raise SystemExit("Set JAVA_HOME to the project's JDK, or add its bin directory to PATH")
java = Path(java_path)
data = json.loads(re.search(r"^  const data = (.*);$", (root / "index.html").read_text(encoding="utf-8"), re.MULTILINE).group(1))
classpath = str(classes) + os.pathsep + str(stdlib)
with tempfile.TemporaryDirectory(prefix="skttp-qr-protocol-") as temporary:
    cases = Path(temporary) / "cases.txt"
    lines = []
    for count, modes in data["datasets"].items():
        for mode, dataset in modes.items():
            lines.append(f"{mode} {count} {len(dataset['frames'])}")
            lines.extend(dataset["frames"])
    cases.write_text("\n".join(lines), encoding="utf-8")
    dynamic = root / "out/dynamic-cases.txt"
    assert dynamic.exists(), "Run node tools/qr-loop-test/verify.cjs first"
    cases.write_text("\n".join(lines) + "\n" + dynamic.read_text(encoding="utf-8"), encoding="utf-8")
    subprocess.run([str(java.with_name("javac.exe" if os.name == "nt" else "javac")), "-encoding", "UTF-8", "-classpath", classpath,
                    "-d", temporary, str(root / "QrTestProtocolCheck.java")], check=True)
    subprocess.run([str(java), "-classpath", temporary + os.pathsep + classpath,
                    "QrTestProtocolCheck", str(cases), "36"], check=True)
    zxing = next((Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/com.google.zxing/core/3.5.4").rglob("core-3.5.4.jar"))
    subprocess.run([str(java.with_name("javac.exe" if os.name == "nt" else "javac")), "-encoding", "UTF-8", "-classpath", str(zxing),
                    "-d", temporary, str(root / "QrTestImages.java")], check=True)
    subprocess.run([str(java), "-classpath", temporary + os.pathsep + str(zxing),
                    "QrTestImages", "--verify-symbols", str(root / "out/dynamic-symbols.txt")], check=True)
