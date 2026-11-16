package jp.linkserver.nittcsc.sktjf

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.time.Instant
import java.util.concurrent.TimeUnit

internal const val SKTJF_DISTRIBUTION_REPOSITORY = "Link2011-Act2/NITTC_Timetable"

internal sealed interface SktjfFetchResult {
    data class Success(val manifest: SktjfManifest, val document: SktjfDocument) : SktjfFetchResult
    data object ReleaseNotFound : SktjfFetchResult
    data object ClassNotFound : SktjfFetchResult
    data object RateLimited : SktjfFetchResult
    data object NetworkFailure : SktjfFetchResult
    data object InvalidData : SktjfFetchResult
    data class HttpFailure(val status: Int) : SktjfFetchResult
}

internal sealed interface SktjfDiscoveryResult {
    data class Found(val revision: String) : SktjfDiscoveryResult
    data object NotPublished : SktjfDiscoveryResult
    data object RateLimited : SktjfDiscoveryResult
    data object NetworkFailure : SktjfDiscoveryResult
    data object InvalidData : SktjfDiscoveryResult
    data class HttpFailure(val status: Int) : SktjfDiscoveryResult
}

/** Downloads and validates into memory only; no user data is written or replaced. */
internal class SktjfDistributionClient(
    private val repository: String,
    client: OkHttpClient = OkHttpClient()
) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS).build()

    init { require(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(repository)) }

    /** A missing or invalid manifest is not evidence that an academic year is available. */
    suspend fun verifiedManifest(revision: String): SktjfManifest? = withContext(Dispatchers.IO) {
        try {
            require(Regex("^\\d{4}\\.[SF]\\.(0|[1-9][0-9]*)$").matches(revision))
            SktjfJson.manifest(download("https://github.com/$repository/releases/download/$revision/manifest.json", 512 * 1024), repository)
                .also { require(it.revision == revision) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }

    /** Scan every listed page before selecting the greatest numeric revision. */
    suspend fun discover(year: Int, term: String): SktjfDiscoveryResult = withContext(Dispatchers.IO) {
        try {
            require(year in 1900..9998 && term in setOf("S", "F"))
            val pattern = Regex("^$year\\.$term\\.(0|[1-9][0-9]*)$")
            var best: Pair<BigInteger, String>? = null
            for (page in 1..20) {
                val bytes = download("https://api.github.com/repos/$repository/releases?per_page=30&page=$page", 4 * 1024 * 1024)
                val wrapped = "{\"releases\":".toByteArray() + bytes + "}".toByteArray()
                val releases = strictSktjfObject(wrapped, 4 * 1024 * 1024 + 32).getJSONArray("releases")
                require(releases.length() <= 30)
                for (index in 0 until releases.length()) {
                    val item = releases.getJSONObject(index)
                    val draft = item.get("draft") as? Boolean ?: error("Invalid draft flag")
                    val prerelease = item.get("prerelease") as? Boolean ?: error("Invalid prerelease flag")
                    val tag = item.get("tag_name") as? String ?: error("Invalid release tag")
                    if (draft || prerelease) continue
                    val match = pattern.matchEntire(tag) ?: continue
                    val published = item.get("published_at") as? String ?: error("Invalid publication date")
                    Instant.parse(published)
                    val revision = BigInteger(match.groupValues[1])
                    if (best == null || revision > best.first) best = revision to tag
                }
                if (releases.length() < 30) return@withContext best?.let { SktjfDiscoveryResult.Found(it.second) }
                    ?: SktjfDiscoveryResult.NotPublished
            }
            SktjfDiscoveryResult.InvalidData
        } catch (e: CancellationException) {
            throw e
        } catch (e: FetchHttpException) {
            when {
                e.rateLimited -> SktjfDiscoveryResult.RateLimited
                else -> SktjfDiscoveryResult.HttpFailure(e.status)
            }
        } catch (_: IOException) {
            SktjfDiscoveryResult.NetworkFailure
        } catch (_: Exception) {
            SktjfDiscoveryResult.InvalidData
        }
    }

    suspend fun fetch(revision: String, classId: String): SktjfFetchResult = withContext(Dispatchers.IO) {
        try {
            require(Regex("^\\d{4}\\.[SF]\\.(0|[1-9][0-9]*)$").matches(revision))
            require(Regex("^(1-[1-4]|[2-5][MEIB]|AC-[A-Z0-9]+)$").matches(classId))
            val url = "https://github.com/$repository/releases/download/$revision/manifest.json"
            val manifest = SktjfJson.manifest(download(url, 512 * 1024), repository)
            require(manifest.revision == revision)
            val asset = manifest.assets.singleOrNull { it.classId == classId } ?: return@withContext SktjfFetchResult.ClassNotFound
            val bytes = download(asset.url, 4 * 1024 * 1024)
            SktjfFetchResult.Success(manifest, SktjfJson.verifiedDocument(bytes, manifest, asset))
        } catch (e: CancellationException) {
            throw e
        } catch (e: FetchHttpException) {
            when {
                e.rateLimited -> SktjfFetchResult.RateLimited
                e.status == 404 -> SktjfFetchResult.ReleaseNotFound
                else -> SktjfFetchResult.HttpFailure(e.status)
            }
        } catch (_: IOException) {
            SktjfFetchResult.NetworkFailure
        } catch (_: Exception) {
            SktjfFetchResult.InvalidData
        }
    }

    private suspend fun download(initialUrl: String, maxBytes: Int): ByteArray {
        var url = requireNotNull(initialUrl.toHttpUrlOrNull())
        repeat(6) { attempt ->
            currentCoroutineContext().ensureActive()
            require(allowedUrl(url))
            val request = Request.Builder().url(url).header("Accept", "application/json")
                .header("User-Agent", "NITTC-Scheduler-SKTJF").build()
            client.newCall(request).execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    require(attempt < 5)
                    url = requireNotNull(response.header("Location")?.let(url::resolve))
                    return@use
                }
                if (!response.isSuccessful) throw FetchHttpException(response.code,
                    response.code == 429 || (response.code == 403 &&
                        (response.header("X-RateLimit-Remaining") == "0" || response.header("Retry-After") != null)))
                val body = requireNotNull(response.body)
                require(body.contentLength() <= maxBytes)
                val output = ByteArrayOutputStream()
                body.byteStream().use { stream ->
                    val buffer = ByteArray(8192); var total = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = stream.read(buffer); if (count < 0) break
                        total += count; require(total <= maxBytes); output.write(buffer, 0, count)
                    }
                }
                return output.toByteArray().also { require(it.isNotEmpty()) }
            }
        }
        throw IllegalArgumentException("Too many redirects")
    }

    private fun allowedUrl(url: HttpUrl): Boolean = url.isHttps && url.port == 443 &&
        url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
        url.host in setOf("github.com", "api.github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
}

private class FetchHttpException(val status: Int, val rateLimited: Boolean) : Exception()
