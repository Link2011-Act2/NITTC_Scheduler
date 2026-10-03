# SKTTP/QR 128枚送信テスト

`index.html`をブラウザで開く。外部ライブラリ・ネット接続・サーバーは不要。
生成済みQR128枚を番号順に繰り返し表示する。全断片が圧縮データ200Bで、全体は25,600B。
速度は200 / 125 / 65 / 50ms。初期値は125ms。ブラウザの描画周期で丸められるため、
端数を次フレームに持ち越して平均速度を保つ。遅いフレームでも断片はスキップしない。

正しいSHA-256、GZIP、v1 JSONを持つ授業メモ2件のテストデータ。
受信検証だけならアプリの上書き確認をキャンセルすること。
テストページの再生成は新しい共有IDになるため、以前の受信セッションとは混在させない。

再生成:

```powershell
python tools/qr-loop-test/generate.py
```

Android StudioのJBRとローカルGradleキャッシュのZXing 3.5.4を使用。
生成時に128枚すべてをZXingで復号し、断片サイズ、全体ハッシュ、JavaのGZIP展開を確認する。
生成された`index.html`だけをコピーして他のPCでも使える。

ページのデータ・再生ロジックの検証（Node.js）:

```powershell
node tools/qr-loop-test/verify.cjs
```
