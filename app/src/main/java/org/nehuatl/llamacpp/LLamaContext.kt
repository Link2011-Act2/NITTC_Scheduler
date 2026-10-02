// Vendored from io.github.ljcamargo:llamacpp-kotlin:0.4.0.
// Native loading is modified for NITTC Scheduler; see third_party/llamacpp/README.md.
package org.nehuatl.llamacpp

import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

class LlamaContext(
    private val id: Int,
    params: Map<String, Any>
) {

    private var tokenCallback: ((String) -> Unit)? = null

    companion object {
        private const val NAME = "RNLlamaContext"

        init {
            LlamaNativeLoader.ensureLoaded()
        }

        private fun isArm64V8a(): Boolean = Build.SUPPORTED_ABIS[0] == "arm64-v8a"
        private fun isX86_64(): Boolean = Build.SUPPORTED_ABIS[0] == "x86_64"
        private fun getCpuFeatures(): String {
            val file = File("/proc/cpuinfo")
            val stringBuilder = StringBuilder()
            try {
                val bufferedReader = BufferedReader(FileReader(file))
                var line: String?
                while (bufferedReader.readLine().also { line = it } != null) {
                    if (line!!.startsWith("Features")) {
                        stringBuilder.append(line)
                        break
                    }
                }
                bufferedReader.close()
                return stringBuilder.toString()
            } catch (e: IOException) {
                Log.w(NAME, "Couldn't read /proc/cpuinfo", e)
                return ""
            }
        }
    }

    val context: Long
    val modelDetails: Map<String, Any>

    init {
        if (!isArm64V8a() && !isX86_64()) {
            throw IllegalStateException("Only 64-bit architectures are supported")
        }
        if (!params.containsKey("model")) {
            throw IllegalArgumentException("Missing required parameter: model")
        }

        this.context = initContextWithFd(
            // int modelFd,
            params["model_fd"] as Int,
            // boolean embedding,
            params["embedding"] as? Boolean ?: false,
            // int n_ctx,
            params["n_ctx"] as? Int ?: 512,
            // int n_batch,
            params["n_batch"] as? Int ?: 512,
            // int n_threads,
            params["n_threads"] as? Int ?: 0,
            // int n_gpu_layers,
            params["n_gpu_layers"] as? Int ?: 0,
            // boolean use_mlock,
            params["use_mlock"] as? Boolean ?: true,
            // boolean use_mmap,
            params["use_mmap"] as? Boolean ?: true,
            // boolean vocab_only,
            params["vocab_only"] as? Boolean ?: false,
            // String lora,
            params["lora"] as? String ?: "",
            // float lora_scaled,
            (params["lora_scaled"] as? Double)?.toFloat() ?: 1.0f,
            // float rope_freq_base,
            (params["rope_freq_base"] as? Double)?.toFloat() ?: 0.0f,
            // float rope_freq_scale
            (params["rope_freq_scale"] as? Double)?.toFloat() ?: 0.0f,
            // int mmproj_fd,
            params["mmproj_fd"] as? Int ?: -1,
            // int[] image_fds
            (params["image_fds"] as? List<Int>)?.toIntArray() ?: intArrayOf()
        )
        if (this.context == 0L) {
            throw IllegalStateException("Failed to initialize llama context")
        }
        this.modelDetails = loadModelDetails(this.context).toMutableMap()
    }

    fun setTokenCallback(callback: (String) -> Unit) {
        tokenCallback = callback
    }

    fun getFormattedChat(messages: List<Map<String, Any>>, chatTemplate: String): String {
        val msgs = messages.toTypedArray()
        return getFormattedChat(context, msgs, chatTemplate.ifEmpty { "" })
    }

    private inner class PartialCompletionCallback {
        private val emitNeeded: Boolean

        constructor(emitNeeded: Boolean) {
            this.emitNeeded = emitNeeded
        }

        fun onPartialCompletion(tokenResult: Map<String, Any>) {
            if (!emitNeeded) return
            tokenCallback?.invoke(tokenResult["token"] as String)
        }
    }

    fun loadSession(path: String): Map<String, Any> {
        if (path.isEmpty()) {
            throw IllegalArgumentException("File path is empty")
        }
        val file = File(path)
        if (!file.exists()) {
            throw IllegalArgumentException("File does not exist: $path")
        }
        val result = loadSession(context, path).toMutableMap()
        if (result.containsKey("error")) {
            throw IllegalStateException(result["error"] as String)
        }
        return result
    }

    fun saveSession(path: String, size: Int): Int {
        if (path.isEmpty()) {
            throw IllegalArgumentException("File path is empty")
        }
        return saveSession(context, path, size)
    }

    fun completion(params: Map<String, Any>): Map<String, Any> {
        if (!params.containsKey("prompt")) {
            throw IllegalArgumentException("Missing required parameter: prompt")
        }

        val logitBias = params["logit_bias"] as? List<List<Double>>
        val logitBiasArray: Array<DoubleArray> = logitBias?.map { it.toDoubleArray() }?.toTypedArray() ?: emptyArray()

        val result = doCompletion(
            context,
            params["prompt"] as String,
            params["grammar"] as? String ?: "",
            (params["temperature"] as? Double)?.toFloat() ?: 0.7f,
            params["n_threads"] as? Int ?: 0,
            params["n_predict"] as? Int ?: -1,
            params["n_probs"] as? Int ?: 0,
            params["penalty_last_n"] as? Int ?: 64,
            (params["penalty_repeat"] as? Double)?.toFloat() ?: 1.00f,
            (params["penalty_freq"] as? Double)?.toFloat() ?: 0.00f,
            (params["penalty_present"] as? Double)?.toFloat() ?: 0.00f,
            (params["mirostat"] as? Double)?.toFloat() ?: 0.00f,
            (params["mirostat_tau"] as? Double)?.toFloat() ?: 5.00f,
            (params["mirostat_eta"] as? Double)?.toFloat() ?: 0.10f,
            params["penalize_nl"] as? Boolean ?: false,
            params["top_k"] as? Int ?: 40,
            (params["top_p"] as? Double)?.toFloat() ?: 0.95f,
            (params["min_p"] as? Double)?.toFloat() ?: 0.05f,
            (params["xtc_t"] as? Double)?.toFloat() ?: 0.00f,
            (params["xtc_p"] as? Double)?.toFloat() ?: 0.00f,
            (params["tfs_z"] as? Double)?.toFloat() ?: 1.00f,
            (params["typical_p"] as? Double)?.toFloat() ?: 1.00f,
            params["seed"] as? Int ?: -1,
            (params["stop"] as? List<String>)?.toTypedArray() ?: emptyArray(),
            params["ignore_eos"] as? Boolean ?: false,
            logitBiasArray,
            (params["image_fds"] as? List<Int>)?.toIntArray() ?: intArrayOf(),
            PartialCompletionCallback(
                params["emit_partial_completion"] as? Boolean ?: false
            )
        ).toMutableMap()
        if (result.containsKey("error")) {
            throw IllegalStateException(result["error"] as String)
        }
        return result
    }

    fun stopCompletion() {
        stopCompletion(context)
    }

    fun isPredicting(): Boolean {
        return isPredicting(context)
    }

    fun tokenize(text: String): List<Int> {
        val result = tokenize(context, text)
        return result.map { it as Int }
    }

    fun detokenize(tokens: List<Int>): String {
        return detokenize(context, tokens.toIntArray())
    }

    fun getEmbedding(text: String): Map<String, Any> {
        if (!isEmbeddingEnabled(context)) {
            throw IllegalStateException("Embedding is not enabled")
        }
        val result = embedding(context, text).toMutableMap()
        if (result.containsKey("error")) {
            throw IllegalStateException(result["error"] as String)
        }
        return result
    }

    fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String {
        return bench(context, pp, tg, pl, nr)
    }

    fun release() {
        freeContext(context)
    }

    private external fun initContextWithFd(
        modelFd: Int,
        embedding: Boolean,
        n_ctx: Int,
        n_batch: Int,
        n_threads: Int,
        n_gpu_layers: Int,
        use_mlock: Boolean,
        use_mmap: Boolean,
        vocab_only: Boolean,
        lora: String?,
        lora_scaled: Float,
        rope_freq_base: Float,
        rope_freq_scale: Float,
        mmprojFd: Int,
        imageFds: IntArray
    ): Long

    private external fun loadModelDetails(contextPtr: Long): Map<String, Any>

    external fun getFormattedChat(contextPtr: Long, messages: Array<Map<String, Any>>, chatTemplate: String): String

    private external fun loadSession(contextPtr: Long, path: String): Map<String, Any>

    private external fun saveSession(contextPtr: Long, path: String, size: Int): Int

    private external fun doCompletion(
        contextPtr: Long,
        prompt: String,
        grammar: String,
        temperature: Float,
        n_threads: Int,
        n_predict: Int,
        n_probs: Int,
        penalty_last_n: Int,
        penalty_repeat: Float,
        penalty_freq: Float,
        penalty_present: Float,
        mirostat: Float,
        mirostat_tau: Float,
        mirostat_eta: Float,
        penalize_nl: Boolean,
        top_k: Int,
        top_p: Float,
        min_p: Float,
        xtc_t: Float,
        xtc_p: Float,
        tfs_z: Float,
        typical_p: Float,
        seed: Int,
        stop: Array<String>,
        ignore_eos: Boolean,
        logit_bias: Array<DoubleArray>,
        imageFds: IntArray,
        partial_completion_callback: PartialCompletionCallback
    ): Map<String, Any>

    private external fun stopCompletion(contextPtr: Long)

    private external fun isPredicting(contextPtr: Long): Boolean

    private external fun tokenize(contextPtr: Long, text: String): List<Any>

    private external fun detokenize(contextPtr: Long, tokens: IntArray): String

    private external fun isEmbeddingEnabled(contextPtr: Long): Boolean

    private external fun embedding(contextPtr: Long, text: String): Map<String, Any>

    private external fun bench(contextPtr: Long, pp: Int, tg: Int, pl: Int, nr: Int): String

    private external fun freeContext(contextPtr: Long)
}
