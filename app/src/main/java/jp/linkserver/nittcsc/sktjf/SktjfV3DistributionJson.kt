package jp.linkserver.nittcsc.sktjf

import java.security.MessageDigest

internal data class SktjfV3Manifest(val common: SktjfManifest, val assetVersions: Map<String, Int>)
internal sealed interface SktjfV3Asset {
    data class V1(val document: SktjfDocument) : SktjfV3Asset
    data class V2(val candidate: SktjfV2Candidate) : SktjfV3Asset
    data class V3(val candidate: SktjfV3Candidate) : SktjfV3Asset
}

/** Mixed releases keep individual asset versions and check original bytes before parsing. */
internal object SktjfV3DistributionJson {
    fun manifest(bytes: ByteArray, repository: String): SktjfV3Manifest {
        val root=strictSktjfObject(bytes,512*1024)
        require(root.integer("dataFormatVersion",3..3)==3)
        val versions=linkedMapOf<String,Int>()
        val assets=root.getJSONArray("assets");require(assets.length() in 1..128)
        for(index in 0 until assets.length()) {
            val asset=assets.getJSONObject(index);val id=asset.text("classId",128)
            require(versions.put(id,asset.integer("dataFormatVersion",1..3))==null)
            asset.put("dataFormatVersion",1)
        }
        root.put("dataFormatVersion",1)
        val common=SktjfJson.manifest(root.toString().toByteArray(Charsets.UTF_8),repository)
        require(common.assets.map { it.classId }.toSet()==versions.keys)
        return SktjfV3Manifest(common,versions)
    }
    fun verifiedDocument(bytes: ByteArray, manifest: SktjfV3Manifest, asset: SktjfAsset): SktjfV3Asset {
        require(bytes.size in 1..4*1024*1024 && asset in manifest.common.assets)
        val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(MessageDigest.isEqual(hash.toByteArray(Charsets.US_ASCII),asset.sha256.toByteArray(Charsets.US_ASCII)))
        val result=when(requireNotNull(manifest.assetVersions[asset.classId])) {
            1 -> SktjfV3Asset.V1(SktjfJson.localDocument(bytes.inputStream()))
            2 -> SktjfV3Asset.V2(SktjfV2CandidateJson.parse(bytes))
            3 -> SktjfV3Asset.V3(SktjfV3CandidateJson.parse(bytes))
            else -> error("Unsupported SKTJF version")
        }
        val document=when(result) {
            is SktjfV3Asset.V1 -> result.document
            is SktjfV3Asset.V2 -> result.candidate.document
            is SktjfV3Asset.V3 -> result.candidate.timetable.document
        }
        require(document.academicYear==manifest.common.academicYear && document.term==manifest.common.term &&
            document.revision==manifest.common.revision && document.classId==asset.classId)
        return result
    }
}
