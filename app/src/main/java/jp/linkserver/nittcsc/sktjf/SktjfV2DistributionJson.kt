package jp.linkserver.nittcsc.sktjf

import java.security.MessageDigest

/** Verify a mixed-version manifest and the exact bytes of each asset. */
internal data class SktjfV2Manifest(val common: SktjfManifest, val assetVersions: Map<String, Int>)
internal sealed interface SktjfCandidateAsset {
    data class V1(val document: SktjfDocument) : SktjfCandidateAsset
    data class V2(val candidate: SktjfV2Candidate) : SktjfCandidateAsset
}

internal object SktjfV2DistributionJson {
    fun manifest(bytes: ByteArray, repository: String): SktjfV2Manifest {
        val root = strictSktjfObject(bytes, 512 * 1024)
        require(root.integer("dataFormatVersion", 2..2) == 2)
        val assets = root.getJSONArray("assets")
        require(assets.length() in 1..128)
        val versions = linkedMapOf<String, Int>()
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val id = asset.text("classId", 128)
            val version = asset.integer("dataFormatVersion", 1..2)
            require(versions.put(id, version) == null)
            asset.put("dataFormatVersion", 1)
        }
        root.put("dataFormatVersion", 1)
        val common = SktjfJson.manifest(root.toString().toByteArray(), repository)
        require(common.assets.map { it.classId }.toSet() == versions.keys)
        return SktjfV2Manifest(common, versions)
    }

    fun verifiedDocument(bytes: ByteArray, manifest: SktjfV2Manifest, asset: SktjfAsset): SktjfCandidateAsset =
        readVerified(bytes, manifest, asset, allowUnconfirmed = false)

    /** Inspection can retain unresolved opportunities; it must never apply the result. */
    fun inspectVerifiedDocument(bytes: ByteArray, manifest: SktjfV2Manifest, asset: SktjfAsset): SktjfCandidateAsset =
        readVerified(bytes, manifest, asset, allowUnconfirmed = true)

    private fun readVerified(bytes: ByteArray, manifest: SktjfV2Manifest, asset: SktjfAsset, allowUnconfirmed: Boolean): SktjfCandidateAsset {
        require(bytes.size in 1..4 * 1024 * 1024 && asset in manifest.common.assets)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(MessageDigest.isEqual(hash.toByteArray(Charsets.US_ASCII), asset.sha256.toByteArray(Charsets.US_ASCII)))
        val version = requireNotNull(manifest.assetVersions[asset.classId])
        val result = when (version) {
            1 -> SktjfCandidateAsset.V1(SktjfJson.localDocument(bytes.inputStream()))
            2 -> SktjfCandidateAsset.V2(if (allowUnconfirmed) SktjfV2CandidateJson.inspect(bytes) else SktjfV2CandidateJson.parse(bytes))
            else -> error("Unsupported SKTJF version")
        }
        val document = when (result) {
            is SktjfCandidateAsset.V1 -> result.document
            is SktjfCandidateAsset.V2 -> result.candidate.document
        }
        require(document.academicYear == manifest.common.academicYear && document.term == manifest.common.term &&
            document.revision == manifest.common.revision && document.classId == asset.classId)
        return result
    }
}
