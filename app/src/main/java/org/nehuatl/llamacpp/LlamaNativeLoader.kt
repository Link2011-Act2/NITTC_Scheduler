package org.nehuatl.llamacpp

import java.io.File

/** JNIのクラス初期化より前にアプリ側が準備する。同じプロセス内で差し替えない。 */
object LlamaNativeLoader {
    private var loaded = false

    @Synchronized
    fun loadDownloaded(file: File) {
        if (loaded) return
        System.load(file.absolutePath)
        loaded = true
    }

    @Synchronized
    fun loadBundled(libraryName: String) {
        if (loaded) return
        System.loadLibrary(libraryName)
        loaded = true
    }

    @Synchronized
    fun ensureLoaded() {
        check(loaded) { "AI runtime must be prepared before initializing llama.cpp" }
    }
}
