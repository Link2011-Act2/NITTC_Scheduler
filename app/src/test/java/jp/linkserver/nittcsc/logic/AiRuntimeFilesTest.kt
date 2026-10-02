package jp.linkserver.nittcsc.logic

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AiRuntimeFilesTest {
    private val bytes = "a pinned native binary".toByteArray()
    private val artifact = AiRuntimeArtifact("test", "arm64-v8a", bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })

    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArrayInputStream {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body)
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(output.toByteArray())
    }

    private fun withDestination(test: (File) -> Unit) {
        val directory = Files.createTempDirectory("ai-runtime-test").toFile()
        try { test(File(directory, artifact.fileName)) }
        finally {
            directory.walkBottomUp().forEach { it.setWritable(true); it.delete() }
        }
    }

    @Test fun installsOnlyCompleteVerifiedBinary() = withDestination { target ->
        extractVerifiedAiRuntime(archive(artifact.fileName to bytes, "LICENSES.txt" to "license".toByteArray()), target, artifact)
        assertTrue(verifyAiRuntimeFile(target, artifact))
        assertArrayEquals(bytes, target.readBytes())
    }

    @Test fun rejectsTamperedBinaryOfSameLength() = withDestination { target ->
        val tampered = bytes.copyOf().apply { this[0] = 0 }
        assertThrows(IllegalStateException::class.java) {
            extractVerifiedAiRuntime(archive(artifact.fileName to tampered), target, artifact)
        }
        assertFalse(target.exists())
    }

    @Test fun rejectsTruncatedOrOversizedBinary() {
        listOf(bytes.copyOf(bytes.size - 1), bytes + byteArrayOf(0)).forEach { invalid ->
            withDestination { target ->
                assertThrows(IllegalStateException::class.java) {
                    extractVerifiedAiRuntime(archive(artifact.fileName to invalid), target, artifact)
                }
                assertFalse(target.exists())
            }
        }
    }

    @Test fun rejectsTraversalAndUnexpectedFiles() {
        listOf("../${artifact.fileName}", "/${artifact.fileName}", "another.so").forEach { name ->
            withDestination { target ->
                assertThrows(IllegalStateException::class.java) {
                    extractVerifiedAiRuntime(archive(artifact.fileName to bytes, name to bytes), target, artifact)
                }
                assertFalse(target.exists())
            }
        }
    }

    @Test fun rejectsArchiveWithoutLibrary() = withDestination { target ->
        assertThrows(IllegalStateException::class.java) {
            extractVerifiedAiRuntime(archive("LICENSES.txt" to bytes), target, artifact)
        }
        assertFalse(target.exists())
    }

    @Test fun rejectsDuplicateLibraries() = withDestination { target ->
        val alternative = "libtesu.so"
        val malformed = archive(artifact.fileName to bytes, alternative to bytes).readBytes()
            .toString(Charsets.ISO_8859_1).replace(alternative, artifact.fileName).toByteArray(Charsets.ISO_8859_1)
        assertThrows(IllegalStateException::class.java) {
            extractVerifiedAiRuntime(ByteArrayInputStream(malformed), target, artifact)
        }
        assertFalse(target.exists())
    }

    @Test fun rejectsOversizedLicense() = withDestination { target ->
        assertThrows(IllegalStateException::class.java) {
            extractVerifiedAiRuntime(archive(artifact.fileName to bytes, "LICENSES.txt" to ByteArray(256 * 1024 + 1)), target, artifact)
        }
        assertFalse(target.exists())
    }

    @Test fun cancellationCleansUpPartialFile() = withDestination { target ->
        var checks = 0
        assertThrows(CancellationException::class.java) {
            extractVerifiedAiRuntime(archive(artifact.fileName to bytes), target, artifact) {
                if (++checks == 3) throw CancellationException()
            }
        }
        assertFalse(target.exists())
    }

    @Test fun refusesToOverwriteExistingFile() = withDestination { target ->
        target.writeText("existing")
        assertThrows(IllegalStateException::class.java) {
            extractVerifiedAiRuntime(archive(artifact.fileName to bytes), target, artifact)
        }
        assertEquals("existing", target.readText())
    }
}
