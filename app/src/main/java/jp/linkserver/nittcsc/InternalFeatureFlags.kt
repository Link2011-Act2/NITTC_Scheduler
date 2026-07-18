package jp.linkserver.nittcsc

object InternalFeatureFlags {
    // falseにすると設定項目を隠し、保存済み設定に関係なく機能を無効化する。
    const val NATURAL_LANGUAGE_TASK_ADD = false

    // タブレット・折りたたみ端末向けの大画面レイアウトをまとめて無効化できる。
    const val ADAPTIVE_LARGE_SCREEN_LAYOUT = true
}
