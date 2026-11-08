package jp.linkserver.nittcsc.update

import jp.linkserver.goldy.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateTransferSnapshotTest {
    private val tag="v1.2.0-IntDev_rev10"
    private val asset=GitHubAsset(42,"nittcsc_test.apk","https://github.com/Link2011-Act2/NITTC_Scheduler/releases/download/$tag/nittcsc_test.apk",1234,"sha256:"+"a".repeat(64))
    private val candidate=UpdateCandidate(GitHubRelease(41,tag,url="https://github.com/Link2011-Act2/NITTC_Scheduler/releases/tag/$tag",assets=listOf(asset)))
    @Test fun catalogSnapshotRoundTripsWithoutSerializingInstallAuthority() {
        val raw=UpdateTransferSnapshot.encode(candidate)
        assertEquals(candidate,UpdateTransferSnapshot.decode(raw))
        assertFalse(raw.contains("InstallRequest")); assertFalse(raw.contains("VerifiedApk"))
    }
    @Test fun changedAssetCannotReuseTheSamePresentationIdentity() {
        val changed=listOf(asset.copy(id=43),asset.copy(size=1235),asset.copy(digest="sha256:"+"b".repeat(64)))
        changed.forEach { assertNotEquals(transferIdentity(candidate),transferIdentity(candidate.copy(release=candidate.release.copy(assets=listOf(it))))) }
    }
    @Test fun restoredExternalAssetAndAiRuntimeCannotBeScheduled() {
        fun rejects(edit: (JSONObject) -> Unit) {
            val raw=JSONObject(UpdateTransferSnapshot.encode(candidate)); edit(raw)
            try { UpdateTransferSnapshot.decode(raw.toString()); fail("Invalid checkpoint metadata accepted") } catch(expected: IllegalArgumentException) { }
        }
        rejects { it.getJSONArray("assets").getJSONObject(0).put("url","https://example.com/fake.apk") }
        rejects { it.getJSONArray("assets").getJSONObject(0).put("id",0) }
        rejects { it.put("tag","ai-runtime-999") }
    }
}
