package jp.linkserver.nittcsc.sktjf

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest
import java.time.LocalDate

class SktjfV2DistributionJsonTest {
    private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/sktjf/$name")).use { it.readBytes() }
    private fun manifestFor(bytes: ByteArray, version: Int): JSONObject {
        val manifest = JSONObject(resource("synthetic-manifest-v1.json").toString(Charsets.UTF_8))
        manifest.put("dataFormatVersion", 2)
        val asset = manifest.getJSONArray("assets").getJSONObject(0)
        asset.put("dataFormatVersion", version)
        asset.put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        return manifest
    }
    @Test fun `v2 candidate manifest accepts separately versioned v1 and v2 assets`() {
        for ((name, version) in listOf("synthetic-v1.json" to 1, "synthetic-v2-week.json" to 2)) {
            val bytes = resource(name)
            val manifest = SktjfV2DistributionJson.manifest(manifestFor(bytes, version).toString().toByteArray(), "example/SKTJF")
            val result = SktjfV2DistributionJson.verifiedDocument(bytes, manifest, manifest.common.assets.single())
            assertTrue(if (version == 1) result is SktjfCandidateAsset.V1 else result is SktjfCandidateAsset.V2)
            assertTrue(runCatching { SktjfV2DistributionJson.verifiedDocument(bytes + byteArrayOf(32), manifest, manifest.common.assets.single()) }.isFailure)
        }
    }
    @Test fun `rejects bad version and untrusted url`() {
        val bytes = resource("synthetic-v2-week.json")
        val badVersion = manifestFor(bytes, 2); badVersion.getJSONArray("assets").getJSONObject(0).put("dataFormatVersion", 3)
        assertTrue(runCatching { SktjfV2DistributionJson.manifest(badVersion.toString().toByteArray(), "example/SKTJF") }.isFailure)
        val badUrl = manifestFor(bytes, 2); badUrl.getJSONArray("assets").getJSONObject(0).put("url", "https://example.com/data")
        assertTrue(runCatching { SktjfV2DistributionJson.manifest(badUrl.toString().toByteArray(), "example/SKTJF") }.isFailure)
    }

    @Test fun `tool candidate keeps official opportunity timing through verified distribution`() {
        val bytes = resource("synthetic-v2-opportunities.json")
        val manifest = SktjfV2DistributionJson.manifest(manifestFor(bytes, 2).toString().toByteArray(), "example/SKTJF")
        val asset = SktjfV2DistributionJson.verifiedDocument(bytes, manifest, manifest.common.assets.single()) as SktjfCandidateAsset.V2
        val candidate = asset.candidate
        assertTrue(candidate.weekEvidenceComplete)
        val eighth = LocalDate.of(2026, 4, 6).plusWeeks(7)
        val ninth = eighth.plusWeeks(1)
        val before = resolveSktjfV2Opportunity(candidate, "shared", 1, eighth, SktjfCycle.A, setOf("DD")) as SktjfWeekSelection.Selected
        val after = resolveSktjfV2Opportunity(candidate, "shared", 1, ninth, SktjfCycle.A, setOf("DD")) as SktjfWeekSelection.Selected
        assertEquals("stable-id", before.value.id)
        assertEquals("after", after.value.id)
        assertTrue(prepareSktjfV2Merge(candidate, setOf("DD"), emptyList(), emptyList(), null) is SktjfV2MergePreparation.Ready)
        assertTrue(runCatching { SktjfV2DistributionJson.verifiedDocument(bytes + byteArrayOf(32), manifest, manifest.common.assets.single()) }.isFailure)
    }

    @Test fun `verified inspection retains unknown opportunities without enabling strict apply`() {
        val json = JSONObject(resource("synthetic-v2-opportunities.json").toString(Charsets.UTF_8))
        json.getJSONArray("weekSeries").getJSONObject(0).getJSONObject("countingPolicy").put("regularDayCounts", JSONObject.NULL)
        val bytes = json.toString().toByteArray()
        val manifest = SktjfV2DistributionJson.manifest(manifestFor(bytes, 2).toString().toByteArray(), "example/SKTJF")
        val asset = manifest.common.assets.single()
        assertTrue(runCatching { SktjfV2DistributionJson.verifiedDocument(bytes, manifest, asset) }.isFailure)
        val inspected = SktjfV2DistributionJson.inspectVerifiedDocument(bytes, manifest, asset) as SktjfCandidateAsset.V2
        assertTrue(!inspected.candidate.weekEvidenceComplete)
        assertTrue(prepareSktjfV2Merge(inspected.candidate, setOf("DD"), emptyList(), emptyList(), null) is SktjfV2MergePreparation.Blocked)
        assertTrue(runCatching { SktjfV2DistributionJson.inspectVerifiedDocument(bytes + byteArrayOf(32), manifest, asset) }.isFailure)
    }
}
