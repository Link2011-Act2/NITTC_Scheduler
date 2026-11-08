package jp.linkserver.nittcsc.update

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.goldy.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** The host's actual SDK configuration and Android inspector; no fake signer or verified receipt. */
class GoldyHostVerificationTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private suspend fun rejects(expected: ApkFailure, release: GitHubRelease, file: File=File(app.applicationInfo.sourceDir)) {
        try { selfApkDownloader(app,file=file).download(release); fail("Untrusted APK accepted") }
        catch(error: ApkException) { assertEquals(expected,error.reason) }
    }
    @Test fun sizeDigestAndReleaseIdentityCannotCreateVerifiedApk() = runBlocking<Unit> {
        val release=selfApkCandidate(app).release
        val asset=release.assets.single()
        rejects(ApkFailure.SizeMismatch,release.copy(assets=listOf(asset.copy(size=asset.size!!+1))))
        rejects(ApkFailure.DigestMismatch,release.copy(assets=listOf(asset.copy(digest="sha256:"+"0".repeat(64)))))
        val tag="v999.0.0-Release"
        rejects(ApkFailure.VersionMismatch,release.copy(tag=tag,url=release.url.replace(release.tag,tag),
            assets=listOf(asset.copy(downloadUrl=asset.downloadUrl.replace(release.tag,tag)))))
        val inspector=AndroidApkInspector(app)
        val installed=inspector.installed()
        try { ApkVerifier.verify(installed.copy(versionCode=installed.versionCode+1),installed,release,ApkVersionBindings.Published); fail("Downgrade accepted") }
        catch(error: ApkException) { assertEquals(ApkFailure.VersionCodeTooLow,error.reason) }
    }
    @Test fun unrelatedSignerCannotBecomeAnInstallRequest() {
        val installed=AndroidApkInspector(app).installed()
        val unrelated=installed.copy(signers=ApkSigners(setOf("0".repeat(64)),false))
        try { ApkVerifier.verify(installed,unrelated,selfApkCandidate(app).release,ApkVersionBindings.Published); fail("Foreign signer accepted") }
        catch(error: ApkException) { assertEquals(ApkFailure.SignerMismatch,error.reason) }
    }
    @Test fun validForeignSignerApkNeverCreatesVerifiedAuthority() = runBlocking<Unit> {
        val fixture=File.createTempFile("goldy-foreign-", ".apk",app.cacheDir)
        try {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("cat /data/local/tmp/nittc-goldy-foreign-signer.apk")).use { input -> fixture.outputStream().use { input.copyTo(it) } }
            val metadata=AndroidApkInspector(app).inspect(fixture)
            assertNotNull("Generate and push the disposable signed fixture before this suite",metadata)
            assertEquals(app.packageName,metadata!!.packageName)
            assertEquals(installedVersionName(app),metadata.versionName)
            rejects(ApkFailure.SignerMismatch,selfApkCandidate(app,fixture).release,fixture)
        } finally { assertTrue(fixture.delete()) }
    }
    @Test fun differentPackageCannotCreateVerifiedApk() = runBlocking<Unit> {
        val fixture=File(InstrumentationRegistry.getInstrumentation().context.applicationInfo.sourceDir)
        val release=selfApkCandidate(app).release
        val asset=release.assets.single().copy(size=fixture.length(),digest=null)
        rejects(ApkFailure.PackageMismatch,release.copy(assets=listOf(asset)),fixture)
    }
}
