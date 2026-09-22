# 設定画面のPreference UI

このデザインはMaterial 3 Expressiveモード専用。通常のMaterial 3モードでは、直前の従来型設定画面を`LegacySettingsScreen.kt`と関連ファイルで表示する。

## 参照元

AOSP `android16-qpr1-release` を参照。内部クラスやSettingsLibへの依存は追加しない。

- [SettingsPreferenceTheme.Expressive](https://android.googlesource.com/platform/packages/apps/Settings/+/android16-qpr1-release/res/values/styles_preference_expressive.xml): Preference専用テーマを継承。
- [Theme.Settings.Expressive](https://android.googlesource.com/platform/packages/apps/Settings/+/android16-qpr1-release/res/values/themes_expressive.xml): 標準項目高さ72dp。
- 継承先の [Preferenceレイアウト](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr1-release/packages/SettingsLib/SettingsTheme/res/layout-v36/settingslib_expressive_preference.xml): 最低72dp、可変高、本文と末尾ウィジェットを横並びにする。
- [角丸寸法](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr1-release/packages/SettingsLib/SettingsTheme/res/values-v36/dimens.xml): 外周20dp。
- [グループ先頭背景](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr1-release/packages/SettingsLib/SettingsTheme/res/drawable-v36/settingslib_round_background_top.xml): 外側と内側で異なる角丸を使う連続したグループ。
- [Preferenceテーマ](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr1-release/packages/SettingsLib/SettingsTheme/res/values-v36/themes_preference_expressive.xml): 見出しにPrimary色、Switch / Dialog / EditText / Dropdownの役割を分離。
- [文字スタイル](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr1-release/packages/SettingsLib/SettingsTheme/res/values-v36/styles_expressive.xml): タイトル16sp、要約14sp。

## Composeでの適用

`ui/components/AppSettingsComponents.kt` に `SettingsSection`、`PreferenceGroup`、`PreferenceRow`、`SwitchPreferenceRow`、`NavigationPreferenceRow`、`ValuePreferenceRow` を用意。設定画面は`LocalUiDesignMode`で切り替える。

- 依頼に合わせ、展開型の大見出しを使わず、通常サイズの固定TopAppBarを使用。
- 1カテゴリを1つの角丸20dpのSurfaceにまとめる。AOSPの行ごとの背景描画を、外周だけをクリップする連続コンテナへ置き換える。
- 基本行高72dp、左右16dp、上下12dp。説明やフォント倍率に応じて高さを増やし、文字を切り捨てない。
- 見出しはlabelLarge / Primary、行タイトルはbodyLarge、要約はbodyMedium。色は既存のMaterialThemeを使用し、Dynamic Colorとダークテーマに追従。
- Switchは行全体に1つのtoggleableセマンティクスを持つ。末尾のSwitchは独立したクリック処理を持たない。
- 入力と選択は `SettingsRows.kt` のダイアログへ移動。入力中は下書きのみ更新し、確定時に元のコールバックを呼ぶ。値の補正、デバウンス、永続化は既存処理を使用する。
- 詳細時刻のドラッグ並べ替え、通知権限への誘導、カレンダー範囲確認、インポート確認、実験的機能の警告は維持。

## 検証

`SettingsScreenDesignTest` は両デザインモード、明暗、Dynamic Color、大きいフォントの操作到達性を扱う。`SettingsItemInteractionTest` は条件付き通知項目、Switchの単一操作、無効状態、数値編集の確定・キャンセルを扱う。端末へのインストールを伴う実行は別途必要。
