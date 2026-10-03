"""Summarize pulled Android results without counting blank negatives as successful QR reads."""
import csv
from pathlib import Path
from collections import defaultdict
import numpy as np

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "out"
rows=list(csv.DictReader((OUT/"results.tsv").open(encoding="utf-8"),delimiter="\t"))
assert len(rows)==623*3, f"Incomplete results: {len(rows)}"
assert len({(r['id'],r['engine']) for r in rows})==len(rows), "Duplicate results"
engines=["java_current","cpp","mlkit"]
names={"java_current":"現行 Java ZXing","cpp":"ZXing-C++","mlkit":"ML Kit"}
groups=defaultdict(list)
for r in rows: groups[r["group"]].append(r)
def correct(r,field="three_correct"): return r[field]=="true"
def rate(rs,field): return f"{sum(correct(r,field) for r in rs)}/{len(rs)} ({100*sum(correct(r,field) for r in rs)/len(rs):.1f}%)"
lines=["# QR読み取りエンジン比較結果", "", (OUT/"device.txt").read_text(), "",
       "## 方法", "",
       "独立テストアプリを開発用スマホで実行。アプリ本体と同じQRエンコーダー（誤り訂正M、余白4モジュール）で生成した画像623件を3エンジンに渡した。現行処理のソースをコピーして実行し、読み取り内容を期待文字列と完全一致で確認。回転・遠近変形・ぼけ等は合成画像であり、実カメラの撮影ではない。",
       "",
       "JavaはTRY_HARDERと既存の90度回転・反転回復を使用。C++はQRのみ、tryHarder/tryRotate/tryInvert/tryDownscaleを有効化。ML KitはQRのみ、同梱モデル、ズームなし。両代替エンジンには既存の追跡cropを追加していない。",
       "",
       "静止画は最初の1フレームと最大3回の同一画像入力を記録。3回以内に読めたら打ち切る。連続QRは1枚1回で追跡状態を維持。順番による温度・初期化の偏りを抑えるためエンジンの実行順をケースごとに交替。所要時間は読取呼び出し（ML Kitは非同期完了待ち込み）で、画像の読み込みとJava側RGBLuminanceSource生成は含まない。",
       "", "## 静止画像（白紙1件と連続転送136件を除く486件）", "",
       "| エンジン | 最初の1回 | 3フレーム以内 | 中央値ms | 95百分位ms |",
       "|---|---:|---:|---:|---:|"]
for e in engines:
    rs=[r for r in rows if r['engine']==e and r['group'] not in ['stream','negative']]
    # Exclude initial 20 cases from timing only; preserve all accuracy observations.
    times=[float(r['first_ms']) for r in rs if int(r['id'][4:])>=20]
    lines.append(f"| {names[e]} | {rate(rs,'first_correct')} | {rate(rs,'three_correct')} | {np.median(times):.1f} | {np.percentile(times,95):.1f} |")
lines+= ["", "## 条件別（3フレーム以内、連続転送は1回）", "",
         "| 条件 | 現行 Java ZXing | ZXing-C++ | ML Kit |", "|---|---:|---:|---:|"]
for group,rs in groups.items():
    if group in ['stream','negative']: continue
    lines.append("| "+group+" | "+" | ".join(rate([r for r in rs if r['engine']==e],'three_correct') for e in engines)+" |")
lines+=["", "## 画面用QRの斜め回転", "",
        "角度が90度の倍数の画像を除く。200B断片、QR表示領域180/256/384px、2種類の内容。",
        "", "| サイズ | 現行 Java ZXing | ZXing-C++ | ML Kit |", "|---|---:|---:|---:|"]
for size in [180,256,384]:
    rs=[r for r in rows if r['group']=='rotation_camera' and f'size={size},' in r['details'] and
        int(r['details'].split('angle=')[1].split(',')[0])%90!=0]
    lines.append("| "+str(size)+"px | "+" | ".join(rate([r for r in rs if r['engine']==e],'three_correct') for e in engines)+" |")
lines+=["", "## 連続QR", "",
        "17枚の有効な転送データを2周。既存Collectorが全断片を揃えてSHA-256・GZIPを検証し、元のJSONへ戻せたことを確認。表示周期を実時間で再現したカメラテストではなく、画像列に対する1回ずつの読取。",
        "", "| 条件 | エンジン | 読めた入力数 | 全断片復元 | 初回復元位置 | 中央値ms | 95百分位ms |", "|---|---|---:|---|---:|---:|---:|"]
for stream in ['clean','diagonal','moving','small']:
    for e in engines:
        rs=[r for r in rows if r['stream']==stream and r['engine']==e]
        done=next((i+1 for i,r in enumerate(rs) if r['stream_completed']=='true'),None)
        times=[float(r['first_ms']) for r in rs]
        lines.append(f"| {stream} | {names[e]} | {rate(rs,'first_correct')} | {'成功' if done else '未完了'} | {done or '-'} | {np.median(times):.1f} | {np.percentile(times,95):.1f} |")
wrong=[r for r in rows if r['wrong']=='true']
neg=[r for r in rows if r['group']=='negative']
lines +=["", f"異なる文字列への誤読: {len(wrong)}件。白紙での誤検出: {sum(r['wrong']=='true' for r in neg)}件（各エンジン1入力のみ）。", "",
         "## 限界", "",
         "この合成画像セットと1台の端末での比較。条件の件数を均等にしていないため、全体成功率は一般的な性能順位ではない。小さい密なQR・強いぼけなど、情報が失われて復号できない条件も含む。斜め画像にはBICUBIC補間、ぼけにはGaussianフィルターを使用。画面端の一部ケースはQRが欠ける。端末カメラのAF、反射、モアレ、手ぶれ、露出、ローリングシャッター、表示更新との位相、CameraXのフレーム破棄、実際の8fps転送時間、電池消費は未検証。", "",
         "この比較の実施時点では本体の読み取りエンジン・依存関係は変更していない。Java版の比較ソースはtools/qr-decoder-benchmark/referenceに保存し、その後のC++差し替えと独立して再実行できる。テストアプリは別applicationIdで、時間割アプリのDBにアクセスしない。"]
first_path=OUT/"results-first.tsv"
if first_path.exists():
    first_rows=list(csv.DictReader(first_path.open(encoding="utf-8"),delimiter="\t"))
    assert len(first_rows)==len(rows)
    first={(r['id'],r['engine']):r for r in first_rows}
    differences=[r for r in rows if first[(r['id'],r['engine'])]['first_correct']!=r['first_correct'] or
                 first[(r['id'],r['engine'])]['three_correct']!=r['three_correct']]
    lines += ["", "## 再現性", "", f"同じ623ケースを2回実行（計3,738ケース・エンジン評価）。1回目と2回目で成功・失敗が変わった評価: {len(differences)}件。表の時間は2回目。", ""]
    for e in engines:
        times=[float(r['first_ms']) for r in first_rows if r['engine']==e and r['group'] not in ['stream','negative'] and int(r['id'][4:])>=20]
        lines.append(f"- 1回目 {names[e]}: 中央値{np.median(times):.1f}ms、95百分位{np.percentile(times,95):.1f}ms。")
lines += ["", "## ビルド検証", "",
          "本体の:app:compileDebugKotlinと:app:testDebugUnitTestは成功（既存190テスト、失敗0、GradleのUP-TO-DATE判定）。独立テストアプリのassembleDebug/lintDebugは成功、lintはエラー0・警告5（既存本体と合わせたSDK/Gradleバージョン、テストアプリのバックアップ定義とアイコン）。準備中にテストプロジェクトのKotlinソース指定が原因でcompileDebugKotlinが失敗したが、AGP 9のAndroidSourceSet.kotlin指定へ修正して解消した。"]
report=ROOT.parent.parent/"docs/qr-decoder-benchmark-results.md"
report.write_text("\n".join(lines)+"\n",encoding="utf-8")
print("\n".join(lines[:25]))
print(f"Report: {report}")
