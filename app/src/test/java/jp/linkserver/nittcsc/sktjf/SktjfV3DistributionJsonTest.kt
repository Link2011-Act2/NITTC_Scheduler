package jp.linkserver.nittcsc.sktjf

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class SktjfV3DistributionJsonTest {
    private fun resource(name: String)=requireNotNull(javaClass.getResourceAsStream("/sktjf/$name")).use { it.readBytes() }
    private fun v3(): ByteArray {
        val root=JSONObject(resource("synthetic-v1.json").toString(Charsets.UTF_8)).put("schemaVersion",3)
        val lessons=root.getJSONArray("lessons");val first=lessons.getJSONObject(0)
        lessons.put(JSONObject(first.toString()).put("id","alternative").put("subject","選択授業"))
        root.put("selectionGroups",JSONArray().put(JSONObject().put("id","group")
            .put("dayOfWeek",first.getInt("dayOfWeek")).put("period",first.getInt("period"))
            .put("lessonIds",JSONArray().put(first.getString("id")).put("alternative"))
            .put("sources",JSONArray(first.getJSONArray("sources").toString()))))
        return root.toString().toByteArray()
    }
    @Test fun mixedVersionsValidateEachAssetAndHash() {
        for ((version,bytes) in listOf(1 to resource("synthetic-v1.json"),2 to resource("synthetic-v2-week.json"),3 to v3())) {
            val manifest=JSONObject(resource("synthetic-manifest-v1.json").toString(Charsets.UTF_8)).put("dataFormatVersion",3)
            manifest.getJSONArray("assets").getJSONObject(0).put("dataFormatVersion",version)
                .put("sha256",MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            val parsed=SktjfV3DistributionJson.manifest(manifest.toString().toByteArray(),"example/SKTJF")
            val asset=parsed.common.assets.single()
            val result=SktjfV3DistributionJson.verifiedDocument(bytes,parsed,asset)
            when(version) {
                1 -> assertTrue(result is SktjfV3Asset.V1)
                2 -> assertTrue(result is SktjfV3Asset.V2)
                3 -> assertEquals("group",(result as SktjfV3Asset.V3).candidate.selectionGroups.single().id)
            }
            assertTrue(runCatching { SktjfV3DistributionJson.verifiedDocument(bytes+byteArrayOf(32),parsed,asset) }.isFailure)
        }
    }
}
