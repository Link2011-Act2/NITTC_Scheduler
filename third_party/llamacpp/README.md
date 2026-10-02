# llama.cpp JNI wrapper

The three Kotlin files in `app/src/main/java/org/nehuatl/llamacpp/` are copied
from the published `io.github.ljcamargo:llamacpp-kotlin:0.4.0` sources JAR.

- Sources JAR SHA-256: `6d8d00bfef29f29007c47228380b518d0f6fe6f58731dd7a3ea4aa659297bbc3`
- AAR SHA-256: `739efec4fbcd8e0c15a0aafb6f96ff3a694a1d8bdcb5f090be08c58c3a06bd59`
- Upstream: <https://github.com/ljcamargo/kotlinllamacpp>
- Modification: `LlamaAndroid` and `LlamaContext` companion initializers call
  `LlamaNativeLoader.ensureLoaded()` instead of independently choosing/loading
  a bundled native library. JNI names and callback signatures are unchanged.
- Notices: `app/src/main/assets/oss_licenses/ai_runtime_licenses.txt`.

Native binaries are obtained from the original AAR through a separate Gradle
configuration. They are never checked into the repository. The app runtime
catalog pins each binary's size and SHA-256 and preserves the upstream CPU
selection logic, falling back to ARMv8 when `/proc/cpuinfo` cannot be read.

## Build and distribution

The default build retains bundled binaries until the runtime assets are published
and the download, vision, and release-build checks below are complete. A downloaded,
verified binary is preferred over the bundled copy. A loaded binary is never
replaced within the same process.

```powershell
# Generate the six CPU-specific ZIPs, each with LICENSES.txt, and SHA256SUMS.txt.
.\gradlew.bat :app:prepareAiRuntimeRelease

# Build without bundled llama.cpp binaries.
.\gradlew.bat :app:cleanPackageDebug :app:assembleDebug -PbundleAiRuntime=false
# Release APK (unsigned unless release signing is configured).
.\gradlew.bat :app:cleanPackageRelease :app:assembleRelease -PbundleAiRuntime=false
# Restore the default build with bundled binaries.
.\gradlew.bat :app:cleanPackageDebug :app:assembleDebug
```

Distribution files are generated in `app/build/ai-runtime-release/`.
Publish the six ZIPs and `SHA256SUMS.txt` to the public repository
`Link2011-Act2/NITTC_Scheduler`, under tag `ai-runtime-0.4.0-r1`.
Use the generated `release-notes.md` as the release body. Keep this tag and its
assets unchanged so older app versions can continue downloading matching code.
App update checking excludes the `ai-runtime-` tag prefix.

When switching the bundling property, recreate package outputs with
`cleanPackageDebug` / `cleanPackageRelease`. Incremental APK packaging can retain
unused ZIP space after removing native entries, which hides the size reduction.
These tasks recreate APK packaging without recompiling unrelated code.

Release uploads require an authenticated GitHub session. Do not put GitHub or
Hugging Face tokens in the app. Public asset downloads use HTTPS with normal
certificate verification and follow only the GitHub asset hosts. Downloaded
code goes into `noBackupFilesDir/ai-runtime/<runtime-version>/`.

## Validation before enabling the lightweight build by default

1. Publish the prepared runtime assets.
2. Install the lightweight APK on an ARM64 device with user permission.
3. Check missing runtime, download/cancel/retry, process restart, offline inference,
   and existing model reuse. Check both image input and text inference.
4. Verify release/R8 inference as well as debug inference. Preserve JNI names.
5. After validation, change the default `bundleAiRuntime` to false.

## Optional real-device smoke test

`AiRuntimeInstrumentedTest` verifies the external load, tokenization, and a short
text completion on a lightweight debug build. It skips bundled builds and builds
without provisioned fixtures, and makes no network requests itself.

Provision these files in the target app's `cache/ai-runtime-test/` directory:

- `runtime.zip`: the ZIP selected for the device by `selectAiRuntimeArtifact`.
- `stories15M-q4_0.gguf`: the public test model from
  <https://huggingface.co/ggml-org/tiny-llamas/blob/main/stories15M-q4_0.gguf>.
  SHA-256: `6151b1929d7f5aa3385d9ddef3393e55587c0a55de661562322bc51dfda93a04`.
  Store the local copy under ignored `out/`, never commit model files.

After the user authorizes installation, build with `:app:assembleDebugAndroidTest
-PbundleAiRuntime=false`, install the app with `adb install -r` and the test APK
with `adb install -r -t`. Use `adb push` to `/data/local/tmp/`, then `adb shell
run-as jp.linkserver.nittcsc cp ... cache/ai-runtime-test/...` to copy the fixtures.
Do not pipe binary files through an interactive shell. Run the one test directly:

```powershell
adb shell am instrument -w -r -e class jp.linkserver.nittcsc.ml.AiRuntimeInstrumentedTest jp.linkserver.nittcsc.test/androidx.test.runner.AndroidJUnitRunner
```

The test removes only a runtime that it installed itself; it does not touch
existing models, the scheduler database, or settings. Remove the two fixture
files and the `/data/local/tmp/` staging files afterward. Keep the test model
separate from the normal model selector. This verifies text inference only;
vision inference and release/R8 execution need separate device checks.

Do not update just the native library: update the runtime tag, vendored wrapper,
binary pins, accompanying notices, and device verification together.
