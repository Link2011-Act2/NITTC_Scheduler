# QR読み取り比較

本体の依存関係を変更せず、Java ZXing 3.5.4（現行のQrCameraDecoder）、
ZXing-C++ 3.1.1、ML Kit同梱版17.3.0を同じ画像で比較する独立Androidアプリ。
Java版は差し替え前の`reference/QrCameraDecoder.kt`を使用し、過去の比較条件を維持する。
packageは`jp.linkserver.nittcsc.qrbenchmark`。本体のDBにはアクセスしない。

1. 本体で`:app:compileDebugKotlin`を実行する。
2. Pillow・NumPyがあるPythonで`generate.py`を実行する。
   GradleキャッシュとAndroid Studio1のJBRを使って本体のエンコーダーを呼び出す。
   参照用Java版Decoderと現行Collectorのソースを`out/app-sources`へコピーする。
3. ルートのGradle Wrapperから以下を実行する。

```powershell
$env:ANDROID_HOME='C:\Users\wwwri\AppData\Local\Android\Sdk'
.\gradlew.bat -p tools/qr-decoder-benchmark/android assembleDebug lintDebug
```

4. 許可された開発用端末へ`android/build/outputs/apk/debug/QR Decoder Benchmark-debug.apk`をインストールする。
5. `adb shell am start -n jp.linkserver.nittcsc.qrbenchmark/.BenchmarkActivity`で開始する。
6. `adb logcat -s QrBenchmark:I '*:S'`で進捗確認。DONEを待つ。
7. 次のファイルを`out/`へ取得する（既存結果は必要に応じて退避する）。

```powershell
adb exec-out run-as jp.linkserver.nittcsc.qrbenchmark cat files/results.tsv
adb exec-out run-as jp.linkserver.nittcsc.qrbenchmark cat files/device.txt
adb exec-out run-as jp.linkserver.nittcsc.qrbenchmark cat files/done.txt
```

8. `analyze.py`で`docs/qr-decoder-benchmark-results.md`を生成する。

手順7は`python collect.py <許可された端末のserial>`でも実行できる。

乱数は固定。623画像（静止QR486、白紙1、17枚×2周×4パターン136）を生成。
静止QRは最大3フレームまで、連続QRは1入力1試行。
読み取り内容は完全一致、連続QRは本体CollectorでSHA-256・GZIP・元JSONを検証する。
結果取得はシェルの出力変換を避け、Pythonのsubprocessによるバイト保存を推奨。

テスト中は画面点灯を維持する。再実行はActivityを停止してから開始する。
テストAPK・生成画像・結果CSV・コピーした本体ソースは`out/`や`build/`内の生成物。
合成画像をスマホ上で復号する比較であり、カメラの光学的な総合テストではない。

## 差し替え後の本体のカメラ経路

本体の`:app:assembleDebug :app:assembleDebugAndroidTest`を実行した後、
`python tools/qr-decoder-benchmark/run_camera_tests.py <許可された端末のserial>`で
本体のC++ DecoderをImageProxy/Y平面経由でテストする。
本体とテストAPKを更新インストールし、対象クラスの8件だけを実行する。
既存アプリデータは保持し、カメラの撮影やDBへの取り込みは行わない。
結果は`out/native-camera-tests.txt`に保存する。ADBの終了コードだけに依存せず、
JUnitの`OK (8 tests)`を確認して失敗時は非ゼロで終了する。
