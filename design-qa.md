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

## 2026-10-03: QR共有項目の連結カード化

- ユーザー指定で、前期の時間割＋前期のA/B表、後期の時間割＋後期のA/B表、授業メモ＋予定＋課題を、それぞれつながったカードに変更。試験時間割と既存の休講・授業変更は単独カード。
- M3E版設定と同じAppSettingsGroupを再利用。外側20dp・内側4dpの角丸、行間2dp、surfaceContainerLowを使用。QR共有はテーマにかかわらず連結表示を指定し、設定画面の既定のテーマ別表示は保持。
- 初回の実画面は通常M3のテーマ設定によって境目なしのカードとなったため、連結表示を指定できる引数を追加して再ビルド・再検証。M3E機能フラグやテーマ設定は変更しない。
- 最終実機UIテスト14件成功（390dp/文字1倍、320dp/文字1.8倍で各7件）。A/B表・時間割の独立した選択と転送範囲を確認。表示密度・文字倍率の復元を再読み取りで確認。既存のテストを実行し、今回の表示変更に対する新規テストは追加しない。
- 通常アプリの実画面を目視確認: `out/qr-import-design/10-connected-share-cards-final.png`, `10-connected-share-cards-bottom.png`。授業メモ・予定・課題の連結、単独の試験時間割、スクロール後のQR表示ボタンを確認。拡大文字の証跡: `qr-import-separate-ab-selection-320-1.8.png`。
- compileDebugKotlin/assembleDebug/assembleDebugAndroidTest/lintDebug成功。新規Lint警告なし。データ・転送形式・選択操作は変更しない。開発用スマホに最終APKを反映し、共有項目の画面を表示した状態にした。

connected share cards result: passed

## 2026-10-03: 共有項目のチェックボックス・文字位置の統一

- 個別選択の授業メモ・予定・課題を基準に全項目の位置を統一。操作可能なCheckboxと、行側で操作を受けるCheckboxの測定幅の差を、共通の48dp領域の中央に配置して解消。
- 実機の通常アプリで、前期/後期の時間割・A/B表、休講・授業変更、授業メモ・予定・課題、試験時間割の文字開始位置がすべてx=252pxで一致することをUI情報とスクリーンショットで確認。証跡: `out/qr-import-design/11-aligned-share-cards.png`, `11-aligned-share-cards-bottom.png`。
- 通常390dp/文字1倍、狭幅320dp/文字1.8倍の既存実機UIテスト14件成功。拡大文字でも折り返しと選択操作を確認。表示密度・文字倍率は元の値への復元を再読み取りで確認。
- compileDebugKotlin/assembleDebug/assembleDebugAndroidTest/lintDebug成功。最終APKを開発用スマホへ反映済み。カード構成・個別選択ボタン・選択処理は保持。

aligned share rows result: passed

## 2026-10-03: A/B表の期間表示の削除

- ユーザー指定により、共有項目の前期・後期A/B表から日付範囲の表示行を削除。タイトルと対象データの説明を残す。
- compileDebugKotlin/assembleDebug成功。開発用スマホへ反映し、両方の項目で日付範囲が表示されないことをUI情報と実画面で確認。証跡: `out/qr-import-design/12-share-ab-without-range.png`。

AB range label removal result: passed

## 2026-10-03: QR共有の入口をアイコン付き大型ボタンへ変更

- ユーザー指定により、共有・読み取りを縦に並ぶアイコン付きの大型ボタンへ変更。共有アイコンとQRスキャンアイコン、titleLargeの見出し、短い操作説明を使用。画面冒頭の長い説明は1文に短縮。
- ボタンは横幅いっぱい、最小高さ112dp、角丸24dp。文字倍率に応じて高さを伸ばし、画面はスクロール可能。共有はprimaryContainer、読み取りは標準のtonal button色を使用。アイコンは隣接ラベルと重複する装飾扱い。
- 実機の幅390dp/文字1倍、幅320dp/文字1.8倍で、2つのボタンの表示とタップを確認。共有→項目選択、読み取り→カメラ、戻る→入口の遷移をそれぞれ確認。画面証跡: `out/qr-import-design/13-qr-home-390-1.0.png`, `13-qr-home-320-1.8.png`。
- 初回の実機確認スクリプトは、起動直後にUI取得が失敗し、古い画面情報で操作して失敗。取得前に古い一時XMLを削除し、最新画面の取得成功後だけ操作するよう修正して再実行。両構成で全操作成功。表示密度・文字倍率の復元を再読み取りで確認。実DBの取り込み・共有送信は実行していない。
- 初回Lintは成功したが、ModifierParameter警告を1件検出。Composableのmodifierを最初の省略可能な引数へ移して解消。画面を独立したQrShareHomeScreenへ分離し、最終APKを開発用スマホに反映。
- 最終compileDebugKotlin/assembleDebug/lintDebug成功。Lintは新規警告なし（0 errors / 201 warnings / 31 hints）。

QR home action buttons result: passed

## 2026-10-03: QR共有の画面遷移アニメーション

- ユーザー指定により、設定画面と同じslideInHorizontally/slideOutHorizontally＋fadeIn/fadeOutを共通のQrShareNavigationへ実装。進むときは右から、戻るときは左から入る既存の設定画面と同じ標準アニメーションを使用。
- 入口・共有項目・QR表示・カメラの全画面遷移、授業メモ/予定/課題の個別選択、取り込みシートの概要/詳細/メモ選択へ適用。選択値は遷移の外で保持し、退出中の画面からの操作を無効化。個別選択のBackHandlerも表示中のページに限定。
- カメラは遷移対象内で表示し、退出開始時点でactiveを無効化して解析を停止。アニメーション終了後、既存のDisposableEffectでカメラを解放。退出中のカメラから確認シートや警告を表示しない。
- 通常390dp/文字1倍と狭幅320dp/文字1.8倍で実機UIテスト16件成功（各8件）。追加した回帰テストで個別選択→完了→再度個別選択→OS戻る後のメモ選択・前後期A/Bの保持と生成データを確認。既存の概要/詳細・メモ承認・取り込み中の操作・ライト/ダークも成功。
- 通常アプリから、入口→共有項目→授業メモ個別選択→戻る→QR生成/表示→入口、入口→カメラ→戻るの操作を確認。生成QRは送信せず、実DBの取り込みも実行しない。端末の表示密度・文字倍率は元の値へ復元して再読み取りで一致を確認。
- 遷移途中の実画面を確認: `out/qr-import-design/14-forward-select.png`, `14-forward-picker.png`, `14-back-picker.png`, `14-forward-scan.png`, `14-back-scan.png`。最初の撮影はタップ直後で遷移開始前だったため、200ms待って撮影し直し、横移動とフェードを目視確認。カメラから戻る画像には端末由来のBluetooth通知が重なっているが、アプリの遷移は確認可能。
- compileDebugKotlin/assembleDebug/assembleDebugAndroidTest/lintDebug成功。Lintは0 errors / 201 warnings / 31 hints（新規警告なし）。Androidテストの非null断言に関する既存の警告2件は実行結果に影響しない。最終APKを開発用スマホへ反映し、入口画面を表示した状態にした。

QR navigation animation result: passed


## QR共有導線と同期ボタン表示（2026-10-03）

- 時間割内の操作行からQRを外し、最上部バーを同期 → QR → 設定の順に変更。
- 時間割以外のタブのその他メニューにも同じ順で配置。QRのBetaフラグを維持。
- ナビゲーション設定を上級者向け機能に改名し、両設定UIに同期表示スイッチを追加。
- DB 51→52の移行時のみ、既存のニックネームとパスワード長が設定済みの場合に表示をオンにする。空の自動作成プロフィールは対象外。新規設定はオフ。
- 起動やプロフィール更新で表示選択を上書きしない。設定はJSONバックアップv17に含め、旧v16ではオフとして読む。QR・端末間同期には含めない。
- 開発端末A069で実際のDB移行、上部バーの順序、設定からのオン／オフ、課題タブのその他メニューからのQR画面起動を確認。表示状態は検証前のオンに復元。
- 単体テスト207件成功。通常ビルド・Lint成功（エラー0、既存Warning 201、Hint 31）。
- 検証途中の :app:compileDebugAndroidTestKotlin はSQL引数配列の型推論で失敗し、arrayOf<Any>で修正。通常設定UIの実機テストは同じ親にある2つのスイッチが一致したため、該当グループの先頭のスイッチを選び、スクロールして操作するよう修正。
- BuildConfig生成を省くテスト再ビルドは :app:compileDebugKotlin でGradleのTask Provider依存エラーとなったため、通常の生成手順に戻した。
- 修正後の実機テスト6件成功（移行、バックアップと旧形式、起動時の選択保持、両設定UI、上部／その他メニュー）。320dp幅・文字倍率1.8でもUIテスト4件成功。端末の物理画面サイズと文字倍率1.0へ復元。
- 最新のアプリAPK／テストAPKの通常生成が成功。テストログは out/qr-import-design/sync-button-normal.txt と sync-button-narrow-large-font.txt に保存（Git対象外）。
