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
import java.time.Instant
import java.util.concurrent.TimeUnit

internal const val SKTJF_DISTRIBUTION_REPOSITORY = "Link2011-Act2/NITTC_Timetable"

internal sealed interface SktjfFetchResult {
    data class Success(val manifest: SktjfManifest, val document: SktjfDocument,
        val timed: SktjfV2Candidate? = null, val selections: SktjfV3Candidate? = null) : SktjfFetchResult
    data object ReleaseNotFound : SktjfFetchResult
    data object ClassNotFound : SktjfFetchResult
    data object RateLimited : SktjfFetchResult
    data object NetworkFailure : SktjfFetchResult
    data object InvalidData : SktjfFetchResult
    data class HttpFailure(val status: Int) : SktjfFetchResult
}

/** A verified candidate remains read-only until the v2 apply path supports every condition. */
internal sealed interface SktjfCandidateFetchResult {
    data class Success(val manifest: SktjfV2Manifest, val asset: SktjfCandidateAsset) : SktjfCandidateFetchResult
    data object ReleaseNotFound : SktjfCandidateFetchResult
    data object ClassNotFound : SktjfCandidateFetchResult
    data object RateLimited : SktjfCandidateFetchResult
    data object NetworkFailure : SktjfCandidateFetchResult
    data object InvalidData : SktjfCandidateFetchResult
    data class HttpFailure(val status: Int) : SktjfCandidateFetchResult
}

internal sealed interface SktjfDiscoveryResult {
    data class Found(val revision: String) : SktjfDiscoveryResult
    data object NotPublished : SktjfDiscoveryResult
    data object RateLimited : SktjfDiscoveryResult
    data object NetworkFailure : SktjfDiscoveryResult
    data object InvalidData : SktjfDiscoveryResult
    data class HttpFailure(val status: Int) : SktjfDiscoveryResult
}

internal sealed interface SktjfVacationFetchResult {
    data class Success(val periods: List<SktjfVacation>) : SktjfVacationFetchResult
    data object NotPublished : SktjfVacationFetchResult
    data object Failed : SktjfVacationFetchResult
}

/** Downloads and validates into memory only; no user data is written or replaced. */
internal class SktjfDistributionClient(
    private val repository: String,
    client: OkHttpClient = OkHttpClient(),
    private val beta: Boolean = false
) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS).build()

    init { require(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(repository)) }

    /** A missing or invalid manifest is not evidence that an academic year is available. */
    suspend fun verifiedManifest(revision: String): SktjfManifest? = withContext(Dispatchers.IO) {
        try {
            require(parseSktjfRevision(revision)?.beta == beta)
            val bytes = download("https://github.com/$repository/releases/download/$revision/manifest.json", 512 * 1024)
            val version = strictSktjfObject(bytes, 512 * 1024).integer("dataFormatVersion", 1..3)
            val manifest = when(version) {
                1 -> SktjfJson.manifest(bytes, repository)
                2 -> SktjfV2DistributionJson.manifest(bytes, repository).common
                else -> SktjfV3DistributionJson.manifest(bytes, repository).common
            }
            require(manifest.revision == revision)
            manifest.takeIf { it.assets.isNotEmpty() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }

    /** Scan every listed page before selecting the greatest numeric revision. */
    suspend fun discover(year: Int, term: String): SktjfDiscoveryResult = discoverRevision(year, term)

    suspend fun discoverVacations(year: Int): SktjfDiscoveryResult = discoverRevision(year, null)

    private suspend fun discoverRevision(year: Int, term: String?): SktjfDiscoveryResult = withContext(Dispatchers.IO) {
        try {
            require(year in 1900..9998 && (term == null || term in setOf("S", "F")))
            val ordering = compareBy<SktjfRevision>({ it.term == "F" }, { it.number })
            var best: Pair<String, SktjfRevision>? = null
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
                    if (draft || prerelease != beta) continue
                    val parsed = parseSktjfRevision(tag) ?: continue
                    if (parsed.year != year || parsed.beta != beta || (term != null && parsed.term != term)) continue
                    if (term == null) {
                        val assets = item.getJSONArray("assets")
                        val names = (0 until assets.length()).map { assets.getJSONObject(it).getString("name") }.toSet()
                        if (VACATION_MANIFEST_NAME !in names || VACATION_ASSET_NAME !in names) continue
                    }
                    val published = item.get("published_at") as? String ?: error("Invalid publication date")
                    Instant.parse(published)
                    if (best == null || ordering.compare(parsed, best.second) > 0) best = tag to parsed
                }
                if (releases.length() < 30) return@withContext best?.let { SktjfDiscoveryResult.Found(it.first) }
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
            require(parseSktjfRevision(revision)?.beta == beta)
            require(isSktjfUndergraduateClass(classId))
            val url = "https://github.com/$repository/releases/download/$revision/manifest.json"
            val manifestBytes = download(url, 512 * 1024)
            val version = strictSktjfObject(manifestBytes, 512 * 1024).integer("dataFormatVersion", 1..3)
            val mixed = if (version == 2) SktjfV2DistributionJson.manifest(manifestBytes, repository) else null
            val mixedV3 = if (version == 3) SktjfV3DistributionJson.manifest(manifestBytes, repository) else null
            val manifest = mixedV3?.common ?: mixed?.common ?: SktjfJson.manifest(manifestBytes, repository)
            require(manifest.revision == revision)
            val asset = manifest.assets.singleOrNull { it.classId == classId } ?: return@withContext SktjfFetchResult.ClassNotFound
            val bytes = download(asset.url, 4 * 1024 * 1024)
            var timed: SktjfV2Candidate? = null
            var selections: SktjfV3Candidate? = null
            val document = if (mixedV3 != null) when(val candidate=SktjfV3DistributionJson.verifiedDocument(bytes,mixedV3,asset)) {
                is SktjfV3Asset.V1 -> candidate.document
                is SktjfV3Asset.V2 -> {
                    val legacy = SktjfLegacySelectionCatalog.adapt(bytes, candidate.candidate)
                    if (legacy == null) timed = candidate.candidate else selections = legacy
                    candidate.candidate.document
                }
                is SktjfV3Asset.V3 -> { selections=candidate.candidate;candidate.candidate.timetable.document }
            } else if (mixed == null) SktjfJson.verifiedDocument(bytes, manifest, asset) else
                when (val candidate = SktjfV2DistributionJson.verifiedDocument(bytes, mixed, asset)) {
                    is SktjfCandidateAsset.V1 -> candidate.document
                    is SktjfCandidateAsset.V2 -> {
                        val legacy = SktjfLegacySelectionCatalog.adapt(bytes, candidate.candidate)
                        if (legacy == null) timed = candidate.candidate else selections = legacy
                        candidate.candidate.document
                    }
                    is SktjfCandidateAsset.V3 -> error("Version 3 is not valid in a version 2 manifest")
                }
            SktjfFetchResult.Success(manifest, document, timed, selections)
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

    /** Verify a mixed-version release without changing the existing v1 apply path. */
    suspend fun fetchCandidate(revision: String, classId: String): SktjfCandidateFetchResult = withContext(Dispatchers.IO) {
        try {
            require(parseSktjfRevision(revision)?.beta == beta)
            require(isSktjfUndergraduateClass(classId))
            val url = "https://github.com/$repository/releases/download/$revision/manifest.json"
            val manifest = SktjfV2DistributionJson.manifest(download(url, 512 * 1024), repository)
            require(manifest.common.revision == revision)
            val asset = manifest.common.assets.singleOrNull { it.classId == classId }
                ?: return@withContext SktjfCandidateFetchResult.ClassNotFound
            val bytes = download(asset.url, 4 * 1024 * 1024)
            SktjfCandidateFetchResult.Success(manifest, SktjfV2DistributionJson.inspectVerifiedDocument(bytes, manifest, asset))
        } catch (e: CancellationException) {
            throw e
        } catch (e: FetchHttpException) {
            when {
                e.rateLimited -> SktjfCandidateFetchResult.RateLimited
                e.status == 404 -> SktjfCandidateFetchResult.ReleaseNotFound
                else -> SktjfCandidateFetchResult.HttpFailure(e.status)
            }
        } catch (_: IOException) {
            SktjfCandidateFetchResult.NetworkFailure
        } catch (_: Exception) {
            SktjfCandidateFetchResult.InvalidData
        }
    }

    suspend fun fetchVacations(revision: String): SktjfVacationFetchResult = withContext(Dispatchers.IO) {
        try {
            require(parseSktjfRevision(revision)?.beta == beta)
            val manifestBytes = try {
                download("https://github.com/$repository/releases/download/$revision/$VACATION_MANIFEST_NAME", VACATION_MANIFEST_LIMIT)
            } catch (error: FetchHttpException) {
                if (error.status == 404) return@withContext SktjfVacationFetchResult.NotPublished
                throw error
            }
            val asset = SktjfVacationJson.manifest(manifestBytes, repository, revision)
            SktjfVacationFetchResult.Success(SktjfVacationJson.periods(download(asset.url, VACATION_ASSET_LIMIT), asset))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SktjfVacationFetchResult.Failed
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
