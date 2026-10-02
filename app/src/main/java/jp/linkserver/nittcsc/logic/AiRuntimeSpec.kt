package jp.linkserver.nittcsc.logic

data class AiRuntimeArtifact(
    val libraryName: String,
    val abi: String,
    val size: Long,
    val sha256: String
) {
    val fileName: String get() = "lib$libraryName.so"
    val assetName: String get() = "$libraryName.zip"
    val downloadUrl: String get() =
        "https://github.com/Link2011-Act2/NITTC_Scheduler/releases/download/$AI_RUNTIME_VERSION/$assetName"
}

const val AI_RUNTIME_VERSION = "ai-runtime-0.4.0-r1"
const val AI_RUNTIME_AAR_SHA256 = "739efec4fbcd8e0c15a0aafb6f96ff3a694a1d8bdcb5f090be08c58c3a06bd59"

// 配布するAARのバイナリを固定する。更新時はZIP・ハッシュ・JNIラッパーを一緒に更新する。
val AI_RUNTIME_ARTIFACTS = listOf(
    AiRuntimeArtifact("rnllama_v8", "arm64-v8a", 5720536, "68dc563723ea017c82e35c9cd7b2c7b72787468ecd2f71d0422abde096bb3110"),
    AiRuntimeArtifact("rnllama_v8_2", "arm64-v8a", 5719256, "aaf0d3851d97f4cd640a68c828d33804585c7263950be212ffa6f588226f2743"),
    AiRuntimeArtifact("rnllama_v8_2_dotprod", "arm64-v8a", 5792120, "728d2bc15f455aa07353944348ea3985d065c9f6ffb432084941b1d2b5c94d53"),
    AiRuntimeArtifact("rnllama_v8_2_i8mm", "arm64-v8a", 5784584, "861ba38da617132036b1d67627e70795d04238d92bbbf2b48c824e17110765d6"),
    AiRuntimeArtifact("rnllama_v8_2_dotprod_i8mm", "arm64-v8a", 5798856, "5faae630b6a765b12b026c850c98d384badb1962123b25d8a06f3d07d37903c8"),
    AiRuntimeArtifact("rnllama_x86_64", "x86_64", 6616120, "57078a0938c44a9a367e6e16d3c0cb94fee2162e3ab7ef387e8685841a949b2f")
)

// 端末のABI順序を尊重する。64bit対応端末でも32bitプロセスへのロードはできない。
fun selectAiRuntimeArtifact(primaryAbi: String?, cpuFeatures: Set<String>): AiRuntimeArtifact? {
    val name = when (primaryAbi) {
        "x86_64" -> "rnllama_x86_64"
        "arm64-v8a" -> {
            val v82 = cpuFeatures.containsAll(setOf("asimd", "crc32", "aes"))
            val dotprod = "dotprod" in cpuFeatures || "asimddp" in cpuFeatures
            val i8mm = "i8mm" in cpuFeatures
            when {
                v82 && dotprod && i8mm -> "rnllama_v8_2_dotprod_i8mm"
                v82 && dotprod -> "rnllama_v8_2_dotprod"
                v82 && i8mm -> "rnllama_v8_2_i8mm"
                v82 -> "rnllama_v8_2"
                else -> "rnllama_v8"
            }
        }
        else -> return null
    }
    return AI_RUNTIME_ARTIFACTS.first { it.libraryName == name }
}

fun isAiRuntimeReleaseTag(tag: String): Boolean = tag.startsWith("ai-runtime-", ignoreCase = true)
