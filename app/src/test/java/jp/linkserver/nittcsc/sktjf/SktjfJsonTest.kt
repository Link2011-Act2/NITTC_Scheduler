package jp.linkserver.nittcsc.sktjf

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class SktjfJsonTest {
    private fun resource(name: String): ByteArray = requireNotNull(javaClass.getResourceAsStream("/sktjf/$name")).use { it.readBytes() }
    private val manifestBytes get() = resource("synthetic-manifest-v1.json")
    private val documentBytes get() = resource("synthetic-v1.json")
    private fun manifest() = SktjfJson.manifest(manifestBytes, "example/SKTJF")
    private fun checked(json: JSONObject): SktjfDocument {
        val bytes = json.toString().toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val original = manifest(); val asset = original.assets.single().copy(sha256 = sha)
        return SktjfJson.verifiedDocument(bytes, original.copy(assets = listOf(asset)), asset)
    }
    private inline fun rejects(action: () -> Unit) { assertTrue("Expected invalid input to be rejected", runCatching(action).isFailure) }

    @Test fun acceptsDistributionProducedByTheSktjfTool() {
        val manifest = manifest(); val doc = SktjfJson.verifiedDocument(documentBytes, manifest, manifest.assets.single())
        assertEquals("1-1", doc.classId)
        assertEquals(SktjfSlotState.EXCLUDED, doc.slotCoverage.single { it.dayOfWeek == 2 }.state)
        assertEquals(0, doc.lessonsFor(emptySet()).size)
        assertEquals("数学", doc.lessonsFor(setOf("DD")).single().subject)
        assertEquals(5, doc.calendar.size)
    }

    @Test fun localFileUsesTheSameStrictValidationAndSizeLimit() {
        assertEquals(SktjfJson.verifiedDocument(documentBytes, manifest(), manifest().assets.single()),
            SktjfJson.localDocument(documentBytes.inputStream()))
        val invalid = JSONObject(documentBytes.toString(Charsets.UTF_8)).put("classId", "1-9")
        rejects { SktjfJson.localDocument(invalid.toString().byteInputStream()) }
        rejects { SktjfJson.localDocument(ByteArray(4 * 1024 * 1024 + 1).inputStream()) }
        rejects { SktjfJson.localDocument(byteArrayOf().inputStream()) }
    }

    @Test fun rejectsDamagedAssetAndManifestMismatch() {
        val manifest = manifest(); val asset = manifest.assets.single()
        rejects { SktjfJson.verifiedDocument(documentBytes + byteArrayOf(32), manifest, asset) }
        rejects { SktjfJson.verifiedDocument(documentBytes, manifest.copy(revision = "2026.S.1"), asset) }
        rejects { SktjfJson.verifiedDocument(documentBytes, manifest, asset.copy(classId = "1-2")) }
    }

    @Test fun rejectsUnpublishedUntrustedAndMalformedManifests() {
        fun altered(edit: (JSONObject) -> Unit) {
            val json = JSONObject(manifestBytes.toString(Charsets.UTF_8)); edit(json)
            rejects { SktjfJson.manifest(json.toString().toByteArray(), "example/SKTJF") }
        }
        altered { it.put("publishedAt", JSONObject.NULL) }
        altered { it.put("dataFormatVersion", 2) }
        altered { it.getJSONArray("assets").getJSONObject(0).put("url", "https://github.com.evil.example/example/SKTJF/releases/download/2026.S.0/1-1.sktjf.json") }
        altered { it.getJSONArray("assets").getJSONObject(0).put("url", "http://github.com/example/SKTJF/releases/download/2026.S.0/1-1.sktjf.json") }
        altered { it.getJSONArray("assets").getJSONObject(0).put("url", "https://github.com/example/SKTJF/releases/download/2026.S.1/1-1.sktjf.json") }
        altered { it.getJSONArray("classIds").put("1-1") }
        rejects { SktjfJson.manifest(manifestBytes, "another/SKTJF") }
    }

    @Test fun rejectsIncompleteConflictingAndUnrecognizedData() {
        fun altered(edit: (JSONObject) -> Unit) { val json = JSONObject(documentBytes.toString(Charsets.UTF_8)); edit(json); rejects { checked(json) } }
        altered { it.getJSONArray("calendar").remove(0) }
        altered { it.getJSONArray("slotCoverage").remove(0) }
        altered { it.getJSONArray("lessons").getJSONObject(0).put("period", 2) }
        altered { it.getJSONArray("lessons").getJSONObject(0).getJSONObject("conditions").getJSONArray("none").put("DD") }
        altered { it.getJSONArray("lessons").getJSONObject(0).put("subject", "※3") }
        altered { it.getJSONArray("lessons").getJSONObject(0).put("extra", true) }
        altered { it.getJSONArray("calendar").getJSONObject(0).getJSONArray("sources").getJSONObject(0).put("sourceId", "unknown") }
        altered { it.getJSONArray("calendar").getJSONObject(0).put("dayType", JSONObject.NULL) }
    }

    @Test fun rejectsDuplicateKeysInvalidUtf8AndExcessiveNesting() {
        val duplicate = manifestBytes.toString(Charsets.UTF_8).replaceFirst("\"format\":", "\"format\":\"SKTJF-MANIFEST\",\"for\\u006dat\":")
        rejects { SktjfJson.manifest(duplicate.toByteArray(), "example/SKTJF") }
        rejects { SktjfJson.manifest(byteArrayOf(0xc3.toByte(), 0x28), "example/SKTJF") }
        val deeplyNested = "[".repeat(40) + "0" + "]".repeat(40)
        rejects { SktjfJson.manifest(deeplyNested.toByteArray(), "example/SKTJF") }
    }
}
