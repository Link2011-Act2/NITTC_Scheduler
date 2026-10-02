package jp.linkserver.nittcsc.ml

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.BuildConfig
import jp.linkserver.nittcsc.logic.AI_RUNTIME_VERSION
import jp.linkserver.nittcsc.logic.extractVerifiedAiRuntime
import jp.linkserver.nittcsc.logic.selectAiRuntimeArtifact
import jp.linkserver.nittcsc.logic.verifyAiRuntimeFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.nehuatl.llamacpp.LlamaContext
import java.io.File
import java.security.MessageDigest

/** 手動で用意する小さなモデルで、APK外の固定済みJNIライブラリを検証する。 */
@RunWith(AndroidJUnit4::class)
class AiRuntimeInstrumentedTest {
    @Test
    fun loadsExternalRuntimeAndGeneratesText() = runBlocking {
        assumeFalse("Build with -PbundleAiRuntime=false", BuildConfig.BUNDLED_AI_RUNTIME)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtures = File(context.cacheDir, "ai-runtime-test")
        val model = File(fixtures, "stories15M-q4_0.gguf")
        val archive = File(fixtures, "runtime.zip")
        assumeTrue("Provision fixtures described in third_party/llamacpp/README.md", model.isFile && archive.isFile)
        val digest = MessageDigest.getInstance("SHA-256").digest(model.readBytes())
            .joinToString("") { "%02x".format(it) }
        assertEquals("6151b1929d7f5aa3385d9ddef3393e55587c0a55de661562322bc51dfda93a04", digest)
        val features = runCatching {
            File("/proc/cpuinfo").useLines { lines ->
                lines.firstOrNull { it.startsWith("Features") }?.substringAfter(':')
                    ?.trim()?.split(Regex("\\s+"))?.toSet().orEmpty()
            }
        }.getOrDefault(emptySet())
        val artifact = requireNotNull(selectAiRuntimeArtifact(Build.SUPPORTED_ABIS.firstOrNull(), features))
        val runtime = File(context.noBackupFilesDir, "ai-runtime/$AI_RUNTIME_VERSION/${artifact.fileName}")
        val installedByTest = !runtime.exists()
        if (installedByTest) {
            runtime.parentFile!!.mkdirs()
            archive.inputStream().use { extractVerifiedAiRuntime(it, runtime, artifact) }
        }
        try {
            assertTrue(verifyAiRuntimeFile(runtime, artifact))
            val manager = AiRuntimeManager(context)
            assertEquals(AiRuntimeAvailability.DOWNLOADED, manager.availability())
            manager.prepareForInference()
            val modelFd = ParcelFileDescriptor.open(model, ParcelFileDescriptor.MODE_READ_ONLY).detachFd()
            val llama = LlamaContext(1, mapOf(
                "model" to model.absolutePath,
                "model_fd" to modelFd,
                "n_ctx" to 128,
                "n_batch" to 32,
                "n_threads" to 2,
                "use_mmap" to false,
                "use_mlock" to false
            ))
            try {
                assertTrue(llama.tokenize("Once upon a time").isNotEmpty())
                val text = StringBuilder()
                llama.setTokenCallback { token -> text.append(token) }
                llama.completion(mapOf(
                    "prompt" to "Once upon a time",
                    "n_predict" to 16,
                    "n_threads" to 2,
                    "temperature" to 0.0,
                    "seed" to 1,
                    "emit_partial_completion" to true
                ))
                assertTrue("Expected generated text through the token callback", text.isNotBlank())
            } finally {
                llama.release()
            }
        } finally {
            // 実機確認用に追加したランタイムだけを削除する。既存のモデル・設定は触らない。
            if (installedByTest) runtime.delete()
        }
    }
}
