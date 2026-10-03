"""Pull only this benchmark's files, preserving UTF-8 bytes through ADB."""
import argparse
import subprocess
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument("serial",help="Authorized development device serial")
args=parser.parse_args()
adb=Path.home()/"AppData/Local/Android/Sdk/platform-tools/adb.exe"
out=Path(__file__).resolve().parent/"out"
def read(name):
    return subprocess.run([str(adb),"-s",args.serial,"exec-out","run-as",
                           "jp.linkserver.nittcsc.qrbenchmark","cat","files/"+name],
                          check=True,capture_output=True).stdout
done=read("done.txt")
assert done.decode().strip()=="623 cases completed", "Benchmark not complete"
for name in ["results.tsv","device.txt","done.txt"]:
    (out/name).write_bytes(read(name))
print(done.decode())
