# QR取り込みシートのDesign QA

- source visual truth: `C:/Users/wwwri/.codex/generated_images/01a0ffdb-ad1b-79b1-9448-51c907ddd5fc/exec-e40296c8-fb86-4b9b-925f-664ca0cf850e.png`
- implementation screenshot: `D:/apps/schedulernittc/out/qr-import-design/qr-import-layout-false-top-390-1.0.png`
- state: ライト・動的カラー、2026年度、追加8/2/3件、全9種受信、メモ競合なし。
- final full-view comparison: `D:/apps/schedulernittc/out/qr-import-design/comparison-final.png`
- final focused comparison: `D:/apps/schedulernittc/out/qr-import-design/comparison-focused-final.png`。同じ年度内置換・全年度注意・詳細操作を並べて確認。
- viewport: Androidネイティブ、実設定の幅390dp/文字1倍と幅320dp/文字1.8倍。ライト/ダーク・動的カラー。CSSサイズは対象外。
- density normalization: 元画像853×1844px（想定390×約843dp、約2.19px/dp）からシート部分(0,288)-(853,1844)、実機1224×2720px（390dp設定では502dpi、約3.14px/dp）から(0,342)-(1224,2638)を切り出し、各幅390pxに縮小。比較画像804×732px。元シート高さ711px、実装732px。Android標準ハンドル・システムバー・端末の縦横比による差は許容し、画面背景とOS領域は比較から除外。

## 比較1: blocked

- [P2] 授業メモが文書ではなく線だけのアイコン。課題・詳細も塗りつぶしが強い。標準Materialのアウトライン文書・クリップボードアイコンへ変更する。
- [P2] 主ボタンの左右余白が本文と同じ24dpで、参考画像の12dpより狭い。フッターを左右12dpにする。
- [P2] 全年度の注意で、A/B表・長期休みの見出しまで赤い。見出しは通常の本文色にし、タグ・注意文に警告色を使う。薄い注意背景とタグの色も区別する。
- 検証不足: テスト内のLocalDensity指定がModalBottomSheetの別ウィンドウに反映されず、320dp/文字1.8倍の画像が通常サイズと同じ。端末の実際の表示設定を一時変更して再検証し、元に戻す。

## 比較2: blocked

- 390dp/文字1倍の実設定で、修正後の操作テスト5件成功。アウトラインアイコン、左右12dpのフッター、注意欄の通常本文色とタグ背景は反映済み。
- [P2] 実際の320dp/文字1.8倍では、年度と説明の横並びにより説明・注意文の幅が狭くなる。ラベルと対象データを縦並びに変更する。メタデータの「年度」も途中で分断されるため、年度はまとまりで改行する。
- 実設定の拡大文字テストは3件失敗。詳細の注意文はLazyColumnの未生成要素なので探索方法を変更する。詳細ページのフッターはウィンドウの実寸で高さを制限し、安定した表示を再確認する。
- evidence: `out/qr-import-design/qr-import-layout-false-top-320-1.8.png`, `out/qr-import-design/qr-import-layout-false-scope-320-1.8.png`, `out/qr-import-design/instrumentation-320-1.8.txt`。
- 端末の表示密度・文字倍率はfinallyで元の値に戻し、再読み取りで復元を確認。

## 必須確認項目

- Fonts/typography: 既存のAndroid日本語フォントとExpressiveTypographyを使用。タイトル→区分→本文の階層、年度ラベルの改行は保持。参照の字形を独自フォントで置換しない。
- Spacing/layout: 追加→年度内置換→全年度注意→詳細→固定アクションの順を保持。フッター左右12dpに修正済み。通常表示は全概要が収まる。狭い画面・大きい文字は本文をスクロールし、年度タグと説明は縦並び。
- Colors/tokens: アプリの動的カラーと固定のエラーカラーを尊重。参照との赤色の色相差は既存テーマ由来として許容。注意の対象名は通常の本文色に修正済み。ダークテーマで警告背景が明色の場合も、暗い本文色と赤い注意文でコントラストを保持。
- Image quality/assets: 写真・画像素材なし。標準Materialのアウトライン課題・予定・文書アイコンに修正済み。画像化したUIや独自作画を使用しない。
- Copy/content: 受信種別のみ表示。課題・予定・メモは追加件数、A/B表・長期休みの全年度置換は概要に表示。詳細の通知・紐づけ説明は現行の取り込み動作に合わせる。

## 比較3: passed

- 比較1のアイコン・ボタン余白・警告の対象名の色を修正し、上記の同一入力の全体/重点比較で再確認。
- 比較2の狭い本文と年度の分断を、BoxWithConstraintsによる縦並びとFlowRowによる年度単位の改行で解消。実ウィンドウ寸法でシート上限を計算する。
- 拡大文字で詳細を開く失敗は、末尾カードが画面外のままテストがタップしたことによる。LazyColumnを末尾までスクロールし、実際のタップで詳細表示・戻るを確認。検査や操作を省略して通過扱いにしていない。
- post-fix evidence: `out/qr-import-design/qr-import-layout-false-scope-320-1.8.png`, `out/qr-import-design/qr-import-layout-true-scope-320-1.8.png`, `out/qr-import-design/qr-import-layout-true-details-320-1.8.png`, `out/qr-import-design/qr-import-memo-comparison-320-1.8.png`。
- 実際のQR画像読み込みからも同じシートを表示。`out/qr-import-design/06-import-sheet-final.png`。背景は実際のQRリーダーで、参考画像のホーム画面との差は画面遷移を保持した結果として許容。
- actionable P0/P1/P2 findings: なし。

## 確認済み・残る限界

- 実設定で実機テスト10件成功（390dp/文字1倍: 5件、320dp/文字1.8倍: 5件）。概要/詳細の移動、メモの既定保持・選択保持・承認対象、取り込み中の戻る/キャンセル禁止、個人データのみの表示、重複、エラー、ライト/ダークを確認。結果は `out/qr-import-design/instrumentation-390-1.0.txt` と `instrumentation-320-1.8.txt`。
- 端末の表示密度・文字倍率は元の設定への復元を読み取りで確認。
- JVMユニットテスト193件成功、compileDebugKotlin/assembleDebug/assembleDebugAndroidTest成功。
- 最初のlintはExperimentalDetector内部のenum解析で失敗。検査を無効化せず、既存と同じsealed型でページ状態を表現して解消。最終lintDebug成功、0 errors / 200 warnings / 31 hints（変更箇所の新規警告なし）。
- 実機の確認用QRは保存せずキャンセルする。実DBの削除・置換を伴う取り込みはこのUI確認では実行しない。
- P3: 最大文字倍率では詳細ページの見出しが複数行になり、末尾の1文字だけが次行になる場合がある。全テキストと戻る操作は利用可能。意味単位での見出し改行は今後の微調整候補。
- TalkBackの音声読み上げ、すべての端末サイズ、全OS版での動作は未検証。見出しセマンティクス、checkbox role、decorative iconの扱い、操作の有効/無効はコードと実機テストで確認。

final result: passed

## 2026-10-03: QR専用の年度・学期範囲への更新

- ユーザー指定の追加要件として、A/B表の共有に年度全体・前期・後期の選択と実際の日付範囲を追加。取り込み概要も新QRでは学期と日付範囲を表示。旧v1/v2の全年度置換だけは従来の警告を維持。
- 新しい選択チップはFlowRowで折り返し、本文と固定フッターの既存構成を維持。実機の通常390dp/文字1倍と狭幅320dp/文字1.8倍で操作を確認。
- 実際のレンダーを確認: `out/qr-import-design/qr-import-scoped-selection-390-1.0.png`, `qr-import-scoped-selection-320-1.8.png`, `qr-import-scoped-ab-390-1.0.png`, `qr-import-scoped-ab-320-1.8.png`。拡大文字では日付と詳細リンクは折り返すが、内容と固定アクションは到達可能。
- 最終実機UIテスト14件成功（各構成7件）。旧QRの警告・追加項目・メモ承認・処理中の操作に加え、新QRの期間表示、対象期間の件数、学期選択の受け渡しを検証。表示密度・文字倍率は元の設定へ復元し、再読み取りで一致を確認。
- 実機Room一時DBテスト5件成功。前期/後期の境界、翌年度の春休みの全文共有、対象期間だけの置換、他年度・振替の保持、春休みの再取り込みでのID保持、空データ、設定不一致・不正データでの保持、旧v2の全年度動作を検証。端末のユーザーDBにはテストデータを書き込まない。結果は `out/qr-import-design/instrumentation-ab-room.txt`。
- JVM204件成功、compileDebugKotlin/assembleDebug/assembleDebugAndroidTest成功。lintDebug成功、0 errors / 200 warnings / 31 hints（前回と同数）。
- 途中のcompileDebugAndroidTestKotlin失敗は、member APIであるassertDoesNotExistの不要なimportが原因。importを削除し再ビルド成功。拡大文字のUIテスト1件の初回失敗はLazyColumnの画面外ボタンが未生成のままperformScrollToしたことが原因。親リストでperformScrollToNodeし、表示確認後の実タップで再実行して全件成功。アプリの操作や検査を省略して通過扱いにしていない。
- QR形式のみv3へ更新。通常のA/B表UI、Roomスキーマ、バックアップJSON、ローカル/Nearby同期プロトコルは変更しない。通知再登録・ウィジェット更新は既存のQR取り込み後処理を使用。

scoped QR result: passed

## 2026-10-03: 学期別A/B共有項目と年度候補の整理

- ユーザー指定により、「前期の時間割 → 前期のA/B表 → 後期の時間割 → 後期のA/B表」の順に独立した選択行を配置。以前のA/B表配下の期間選択チップを廃止し、前期・後期のチェック状態から転送範囲を決定する。両方選択は年度全体、未選択はA/B表を送らない。時間割のチェックとは独立している。
- 年度候補は現在年度と時間割・試験時間割が用意されている年度に限定。通常のA/B日・休日・長期休みの日付からは年度候補を増やさない。春休みが翌年度にまたがるケースと、翌年度の準備があるケースを回帰テストで確認。
- 実機UIテスト14件成功（390dp/文字1倍と320dp/文字1.8倍で各7件）。前期のみ・後期のみ・両方・未選択の転送範囲、時間割の独立した選択、不要な2027年度候補の不在を確認。表示密度・文字倍率を元の値へ復元し、再読み取りで一致を確認。
- 画面証跡: `out/qr-import-design/qr-import-separate-ab-selection-390-1.0.png`, `qr-import-separate-ab-selection-320-1.8.png`。通常のアプリから開いた実機画面も確認: `out/qr-import-design/08-separated-ab-actual.png`。現在の2026年度だけが表示され、各A/B表が対応する時間割の直下にある。端末のユーザーDBにはテストデータを書き込んでいない。
- JVM207件成功。compileDebugKotlin/testDebugUnitTest/assembleDebug/assembleDebugAndroidTest/lintDebug成功。Lintは0 errors / 201 warnings / 31 hints。以前の説明文リソースqr_days_scopeが未使用になった警告が1件増えており、実行時の影響はない。Androidテストの不要な非null断言に関するコンパイラ警告2件も実行結果には影響しない。
- QR形式・データ保存・取り込み処理は変更せず、既存の学期範囲を選択UIから渡す。実機には更新APKを反映し、共有項目の選択画面を表示した状態にした。

separate semester selection result: passed
