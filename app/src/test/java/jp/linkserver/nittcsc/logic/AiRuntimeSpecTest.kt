package jp.linkserver.nittcsc.logic

import org.junit.Assert.*
import org.junit.Test

class AiRuntimeSpecTest {
    @Test fun selectsCpuOptimizationsWithSafeFallback() {
        val v82 = setOf("asimd", "crc32", "aes")
        assertEquals("rnllama_v8", selectAiRuntimeArtifact("arm64-v8a", emptySet())?.libraryName)
        assertEquals("rnllama_v8", selectAiRuntimeArtifact("arm64-v8a", setOf("i8mm", "dotprod"))?.libraryName)
        assertEquals("rnllama_v8_2", selectAiRuntimeArtifact("arm64-v8a", v82)?.libraryName)
        assertEquals("rnllama_v8_2_dotprod", selectAiRuntimeArtifact("arm64-v8a", v82 + "asimddp")?.libraryName)
        assertEquals("rnllama_v8_2_i8mm", selectAiRuntimeArtifact("arm64-v8a", v82 + "i8mm")?.libraryName)
        assertEquals("rnllama_v8_2_dotprod_i8mm", selectAiRuntimeArtifact("arm64-v8a", v82 + setOf("dotprod", "i8mm"))?.libraryName)
    }

    @Test fun rejectsUnsupportedAbis() {
        listOf(null, "armeabi-v7a", "x86", "unknown").forEach {
            assertNull(selectAiRuntimeArtifact(it, emptySet()))
        }
        assertEquals("rnllama_x86_64", selectAiRuntimeArtifact("x86_64", emptySet())?.libraryName)
    }

    @Test fun reservedTagsDoNotMatchAppReleases() {
        assertTrue(isAiRuntimeReleaseTag("ai-runtime-0.4.0-r1"))
        assertTrue(isAiRuntimeReleaseTag("AI-RUNTIME-0.5.0-r2"))
        assertFalse(isAiRuntimeReleaseTag("v1.1.2-Release"))
    }
}
