package jp.linkserver.nittcsc.logic

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

fun verifyAiRuntimeFile(file: File, artifact: AiRuntimeArtifact): Boolean {
    if (!file.isFile || file.length() != artifact.size) return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) } == artifact.sha256
}

/** ZIP内のパスを保存先に使わない。固定名の1本とライセンス以外は拒否する。 */
fun extractVerifiedAiRuntime(
    input: InputStream,
    destination: File,
    artifact: AiRuntimeArtifact,
    checkCancelled: () -> Unit = {}
) {
    check(!destination.exists()) { "Destination already exists" }
    var found = false
    var foundLicense = false
    try {
        ZipInputStream(input).use { zip ->
            while (true) {
                checkCancelled()
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    artifact.fileName -> {
                        check(!found && !entry.isDirectory) { "Duplicate or invalid library entry" }
                        found = true
                        FileOutputStream(destination).use { output ->
                            // 開いたFDで書き込む前に読み取り専用にし、書き換え可能な実行ファイルを残さない。
                            check(destination.setReadOnly()) { "Cannot protect AI runtime" }
                            val buffer = ByteArray(64 * 1024)
                            var size = 0L
                            while (true) {
                                checkCancelled()
                                val count = zip.read(buffer)
                                if (count == -1) break
                                size += count
                                check(size <= artifact.size) { "AI runtime exceeds expected size" }
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                    "LICENSES.txt" -> {
                        check(!foundLicense && !entry.isDirectory) { "Duplicate or invalid license entry" }
                        foundLicense = true
                        var size = 0
                        val buffer = ByteArray(4096)
                        while (true) {
                            checkCancelled()
                            val count = zip.read(buffer)
                            if (count == -1) break
                            size += count
                            check(size <= 256 * 1024) { "License entry too large" }
                        }
                    }
                    else -> error("Unexpected AI runtime archive entry")
                }
                zip.closeEntry()
            }
        }
        check(found && verifyAiRuntimeFile(destination, artifact)) { "AI runtime integrity check failed" }
    } catch (failure: Throwable) {
        // WindowsのJVMテストでも、読み取り専用の未検証ファイルを確実に削除する。
        destination.setWritable(true)
        destination.delete()
        throw failure
    }
}
