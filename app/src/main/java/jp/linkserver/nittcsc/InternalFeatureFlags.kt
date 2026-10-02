package jp.linkserver.nittcsc

object InternalFeatureFlags {
    // QRによる一方向のデータ共有（Beta）。保存状態に関係なく入口と処理を無効化できる。
    const val QR_SHARE_BETA = true

    // falseにすると設定項目を隠し、保存済みの選択に関係なく標準Material 3へ戻す。
    const val MATERIAL_3_EXPRESSIVE = false

    // 新規利用者は従来の初期値で直接開始する。
    const val INITIAL_SETUP = false

    // A/B・テスト時間割は利用可能なまま、新しい有効/無効スイッチを隠す。
    const val SPECIAL_TIMETABLE_TOGGLES = false

    // falseにすると設定項目を隠し、保存済み設定に関係なく機能を無効化する。
    const val NATURAL_LANGUAGE_TASK_ADD = false

    // タブレット・折りたたみ端末向けの大画面レイアウトをまとめて無効化できる。
    const val ADAPTIVE_LARGE_SCREEN_LAYOUT = true
}
