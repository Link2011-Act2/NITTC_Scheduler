"""Run the production JNI decoder tests on an authorized device and fail on JUnit failures."""
import argparse
import subprocess
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument("serial")
args=parser.parse_args()
root=Path(__file__).resolve().parent
repo=root.parent.parent
adb=Path.home()/"AppData/Local/Android/Sdk/platform-tools/adb.exe"
base=[str(adb),"-s",args.serial]
for apk in [repo/"app/build/outputs/apk/debug/app-debug.apk",
            repo/"app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]:
    subprocess.run(base+["install","-r",str(apk)],check=True)
result=subprocess.run(base+["shell","am","instrument","-w","-r","-e","class",
    "jp.linkserver.nittcsc.qr.QrCameraDecoderTest",
    "jp.linkserver.nittcsc.test/androidx.test.runner.AndroidJUnitRunner"],capture_output=True)
output=result.stdout.decode("utf-8",errors="replace")+result.stderr.decode("utf-8",errors="replace")
(root/"out/native-camera-tests.txt").write_text(output,encoding="utf-8")
if result.returncode!=0 or "OK (8 tests)" not in output:
    lines=[line for line in output.splitlines() if any(key in line for key in ["test=","stack=","AssertionError","Exception","FAILED","Tests run:"])]
    print("\n".join(lines[:35]))
    raise SystemExit("Native decoder tests failed; full output saved under out/")
print("Production native decoder: OK (8 tests)")
