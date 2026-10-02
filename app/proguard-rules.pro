# WorkManagerはInputMergerをクラス名と引数なしコンストラクターで生成する。
# R8の最適化でコンストラクターが消えると、授業通知を含むOneTimeWorkが実行できない。
-keep class * extends androidx.work.InputMerger {
    public <init>();
}

# ダウンロードする固定済みネイティブライブラリのJNI名とコールバック名を維持する。
-keep class org.nehuatl.llamacpp.** { *; }
