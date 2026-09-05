# AGENTS.md

この文書は、このリポジトリで作業するコーディングエージェント向けの実務ルールです。
対象はリポジトリ全体です。より深いディレクトリに別の `AGENTS.md` がある場合は、そちらを優先してください。

## プロジェクト概要

- NITTC Schedulerは、鶴岡高専向けのAndroid時間割・課題・予定管理アプリです。
- A/B授業日、日程振替、休講・授業変更、試験時間割、課題・予定、通知、端末カレンダー、ウィジェット、端末間同期を扱います。
- 単一のGradleモジュール `:app` で構成されています。
- アプリIDと基底パッケージは `jp.linkserver.nittcsc` です。
- `minSdk = 26`、`targetSdk = 36`、`compileSdk = 36`、JVM 17を使用します。
- UIはJetpack Compose Material 3、永続化はRoom、バックグラウンド処理はWorkManagerとAlarmManagerです。
- Glanceウィジェット、Nearby Connections、OkHttp、Markwon、ML Kit、llama.cppラッパーも使用します。

## 主要な構成

- `app/src/main/java/jp/linkserver/nittcsc/MainActivity.kt`: 依存関係の生成、Composeの起動、通知再登録、ウィジェット更新。
- `data/Entities.kt`: Roomエンティティと主要データモデル。
- `data/SchedulerDao.kt`: Room DAO。
- `data/AppDatabase.kt`: DB定義、DBバージョン、全マイグレーション。
- `data/SchedulerRepository.kt`: データ操作、時間割生成、アプリの主要なドメイン処理。
- `data/SchedulerDataTransfer.kt`: JSONエクスポート・インポートと同期ペイロード。
- `logic/`: UIやAndroid APIに依存しない計算・検索ロジック。可能な限りここへ純粋関数を置く。
- `viewmodel/SchedulerViewModel.kt`: RepositoryのFlowをUI stateへまとめ、UIイベントを処理する。
- `ui/NittcSchedulerApp.kt`: 画面遷移と時間割画面の中心。これ以上肥大化させず、新しいまとまりは別ファイルへ分離する。
- `ui/SettingsScreen.kt`: 設定画面。設定カテゴリと既存の視覚言語を維持する。
- `reminder/`: 課題・予定・授業開始通知、正確なアラーム、Live Updates。
- `sync/`: ローカル同期、Nearby同期、競合判定。
- `calendar/`: 端末カレンダーへの授業・課題・予定の反映。
- `widget/`: Glanceウィジェットと更新処理。
- `update/` と `ui/UpdateOverviewScreen.kt`: GitHubリリース確認、ダウンロード、インストール。
- `ml/`: ローカルAI、OCR、モデルダウンロード。
- `app/src/main/res/values/strings.xml`: ユーザー向け文字列の配置先。
- `app/src/test/`: JVMユニットテスト。現在は時刻設定、タスク検索、同期競合ロジックを主に検証する。

## ビルドと検証

Windows PowerShellでは、Android Studio同梱JBRを使って次を実行できます。

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

- 小さなKotlin/UI変更でも、最低限 `:app:compileDebugKotlin` を実行する。
- `logic/`、同期、日付・時刻計算、シリアライズを変更したら `:app:testDebugUnitTest` も実行する。
- Manifest、権限、Compose API、リソース、依存関係を変更したら `:app:lintDebug` も実行する。
- APK生成が必要な場合は `.\gradlew.bat :app:assembleDebug` を使う。
- 実機確認をしていない場合は、完了報告で明記する。
- 失敗した検証を隠さず、失敗したタスク名と原因を報告する。

## 実装方針

- 既存のCompose Material 3の見た目、色、余白、コンポーネントを優先する。
- 狭いスマートフォン、長い日本語、フォント倍率の増加を前提にする。固定幅を追加する場合は、長文・複数行・省略表示を確認する。
- Composable内に重い検索、DB処理、JSON処理を入れない。純粋計算は `logic/`、データ処理はRepository/DataTransferへ置く。
- UIからDAOを直接呼ばない。原則として `UI -> ViewModel -> Repository -> DAO` の流れを保つ。
- 画面状態は既存のFlowと `SchedulerUiState` に統合し、同じデータの別の状態管理を増やさない。
- `NittcSchedulerApp.kt` や `SettingsScreen.kt` に大きな機能を直接追加し続けず、画面または再利用可能なまとまりでファイル分割する。
- `LocalDate`、`LocalTime`、`YearMonth` を優先し、端末タイムゾーンが必要な境界でのみepoch値へ変換する。
- 新しいユーザー向け文言、ボタン名、エラー、content descriptionは `strings.xml` に追加する。デバッグ専用ログは対象外。
- アイコンだけの操作にはcontent descriptionを設定する。
- 高頻度再コンポーズ箇所では、正しいキーを持つ `remember`、事前のgroup化、安定したリストキーを使う。ただし根拠のない `remember` やキャッシュは増やさない。
- 実験的機能を追加する場合は、保存済み設定だけでなく `InternalFeatureFlags` で完全に非表示・無効化できる既存方式を検討する。

## データベースと互換性

- Room DBの現在のバージョンは `45`。スキーマ変更時はバージョンを1つ上げ、`MIGRATION_45_46` のような連続マイグレーションを追加し、`addMigrations` に登録する。
- ユーザーデータを消す `fallbackToDestructiveMigration` は追加しない。
- エンティティ変更時は、DAO、デフォルト値、マイグレーション、JSON入出力、同期ペイロードへの影響を一緒に確認する。
- 複数テーブルを同時に更新する操作は `db.withTransaction` を使用する。
- 時間割期間を変更した場合、`syncDayTypes()` が期間内の日付行を再構築することを考慮する。
- 既存データの欠落を許容するデフォルト値を用意し、古いJSON・古い同期相手との後方互換性を可能な限り維持する。

## JSONと端末間同期

- JSONエクスポートは、ユーザーのバックアップ用途として設定を含むほぼ全データを対象とする。
- ローカル同期とNearby同期は、設定を移行せず、時間割・A/B日・課題・予定などのデータだけを同期する。この境界を変更しない。
- カレンダーイベントIDなど端末固有IDは、他端末へそのまま移行しない。
- 同期対象を追加したら、`SchedulerRepository.SYNC_DATASET_KEYS`、`SchedulerDataTransfer` の入出力、`touchSyncDatasetMeta`、`SyncPayloadCoordinator`、`LocalSyncManager`、`NearbySyncManager` をすべて確認する。
- 同期ペイロード、同期対象、競合・マージ判定、ローカル同期またはNearby同期の通信手順を変更したら、`data/SyncProtocolVersion.kt` の `CURRENT_SYNC_PROTOCOL_VERSION` を必ず1増やし、旧バージョンとの互換性確認とテストを追加する。バージョン情報を持たない従来版は `LEGACY_SYNC_PROTOCOL_VERSION = 0` として扱う。
- 端末間同期プロトコルの仮称は `SKTTP`。表記が必要になったら `SKTTP/<version>` を使用する。名称の展開は未確定で、内輪案は `Shit school, Kosen Timetable Transfer Protocol`、公開向け候補は `Scheduler Kosen Timetable Transfer Protocol` とする。
- 同期競合を黙って上書きしない。既存の競合確認・自動承認UIの方針を維持する。
- インポート形式を変更したらエクスポートバージョンを更新し、可能なら旧バージョンの読み込みテストを追加する。

## 通知・カレンダー・ウィジェット

- 課題・予定の期限、リマインド、授業時刻を変更したら、対応するWorker/Alarmの再登録とキャンセルを確認する。
- 授業時間、休講、授業変更、日程振替、試験時間割を変更したら、授業開始通知、端末カレンダー、今日/今週/次の授業ウィジェットへの影響を確認する。
- 正確な通知はAlarmManagerとWorkManagerのフォールバックを併用している。片方だけ直して挙動を分岐させない。
- 通知権限や正確なアラーム権限はOSバージョンで異なる。APIレベルをガードし、権限APIを呼ぶ前にManifest宣言も確認する。
- Android 16のLive Updates用APIは、Android 16未満でも安全に動作するように保つ。
- ウィジェットはアプリ本体と別プロセス・別タイミングでデータを読む前提で、UI内の一時状態に依存させない。
- カレンダー同期は重複作成とアプリ由来イベントの一括削除を壊さないよう、イベント識別方法を維持する。

## セキュリティとローカルデータ

- Hugging Faceトークン、同期パスワード、端末識別情報をログへ出さない。
- `local.properties`、DB、APK、モデル、トークンをGitへ追加しない。
- 外部から受け取るJSON、同期ペイロード、Markdown、ファイルURIは不正・欠損値を前提に検証する。
- APKインストール、カメラ、カレンダー、Nearby、通知の権限を追加・変更する場合は、Manifestと実行時権限UIを同時に確認する。

## 生成物と依存関係

- `app/build/`、ルート `build/`、`.gradle/`、APK、一時AAR、端末DBは編集・コミットしない。
- OSSライセンス一覧は `app/build.gradle.kts` の `generateOssLicensesAutoJson` が生成する。生成JSONを直接編集しない。
- ライブラリを追加・更新したら、リリースビルドへのサイズ影響、minSdk、ProGuard/R8、OSSライセンス生成を確認する。
- バージョン名、versionCode、ビルド番号生成は `app/build.gradle.kts` にある。依頼なしにリリース番号を変更しない。

## 変更時の注意

- `targetSdk` を37以上へ上げる際は、Nearby Connections向けに `ACCESS_LOCAL_NETWORK` をManifestへ追加し、Android 17以上で実行時権限の確認・要求・拒否時の案内を実装する。
- 作業開始時に `git status --short` を確認し、既存の未コミット変更を上書き・削除・整形しない。
- 無関係なファイルの一括フォーマットや改行コード変更を避け、差分をタスクに限定する。
- 手動編集は小さな差分にし、既存の日本語コメント・命名・UI表現との一貫性を保つ。
- バグ修正では、可能ならAndroid非依存部分を純粋関数へ分離して再現テストを追加する。
- コミット、タグ、push、APKの実機インストールは、ユーザーから明示的に依頼された場合のみ行う。
