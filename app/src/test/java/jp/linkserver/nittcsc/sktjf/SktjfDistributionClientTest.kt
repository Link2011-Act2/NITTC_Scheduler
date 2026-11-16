package jp.linkserver.nittcsc.sktjf

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SktjfDistributionClientTest {
    private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/sktjf/$name")).use { it.readBytes() }
    private val manifest get() = resource("synthetic-manifest-v1.json")
    private val document get() = resource("synthetic-v1.json")
    private fun client(handler: (Interceptor.Chain) -> Response): SktjfDistributionClient =
        SktjfDistributionClient("example/SKTJF", OkHttpClient.Builder().addInterceptor(Interceptor(handler)).build())
    private fun response(chain: Interceptor.Chain, code: Int, bytes: ByteArray = byteArrayOf(), headers: Map<String, String> = emptyMap()): Response {
        val builder = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .body(bytes.toResponseBody("application/json".toMediaType()))
        headers.forEach { (key, value) -> builder.header(key, value) }
        return builder.build()
    }

    @Test fun retrievesAndChecksBothAssetsWithoutWritingData() = runBlocking {
        val seen = mutableListOf<String>()
        val result = client { chain ->
            val url = chain.request().url.toString(); seen += url
            response(chain, 200, if (url.endsWith("manifest.json")) manifest else document)
        }.fetch("2026.S.0", "1-1")
        assertTrue(result is SktjfFetchResult.Success)
        assertEquals("数学", (result as SktjfFetchResult.Success).document.lessons.single().subject)
        assertEquals(2, seen.size)
    }

    @Test fun productionRepositoryIsUsedForDiscoveryAndVerifiedAssets() = runBlocking {
        assertEquals("Link2011-Act2/NITTC_Timetable", SKTJF_DISTRIBUTION_REPOSITORY)
        val productionManifest = manifest.toString(Charsets.UTF_8)
            .replace("example/SKTJF", SKTJF_DISTRIBUTION_REPOSITORY).toByteArray()
        val urls = mutableListOf<String>()
        val client = SktjfDistributionClient(SKTJF_DISTRIBUTION_REPOSITORY,
            OkHttpClient.Builder().addInterceptor { chain ->
                val url = chain.request().url.toString(); urls += url
                when {
                    url.startsWith("https://api.github.com/") -> response(chain, 200,
                        """[{"tag_name":"2026.S.0","draft":false,"prerelease":false,"published_at":"2026-04-01T00:00:00Z"}]""".toByteArray())
                    url.endsWith("manifest.json") -> response(chain, 200, productionManifest)
                    else -> response(chain, 200, document)
                }
            }.build())
        assertEquals(SktjfDiscoveryResult.Found("2026.S.0"), client.discover(2026, "S"))
        assertTrue(client.fetch("2026.S.0", "1-1") is SktjfFetchResult.Success)
        assertTrue(urls[0].startsWith("https://api.github.com/repos/Link2011-Act2/NITTC_Timetable/releases?"))
        assertTrue(urls.drop(1).all { it.startsWith("https://github.com/Link2011-Act2/NITTC_Timetable/releases/download/2026.S.0/") })
        assertTrue(runCatching { SktjfJson.manifest(manifest, SKTJF_DISTRIBUTION_REPOSITORY) }.isFailure)
    }

    @Test fun rejectsChangedBytesAndUnexpectedVersions() = runBlocking {
        val corrupt = client { chain -> response(chain, 200, if (chain.request().url.encodedPath.endsWith("manifest.json")) manifest else document + byteArrayOf(32)) }
        assertEquals(SktjfFetchResult.InvalidData, corrupt.fetch("2026.S.0", "1-1"))
        val wrong = client { chain -> response(chain, 200, manifest) }
        assertEquals(SktjfFetchResult.InvalidData, wrong.fetch("2026.S.1", "1-1"))
        assertEquals(SktjfFetchResult.ClassNotFound, wrong.fetch("2026.S.0", "1-2"))
    }

    @Test fun distinguishesMissingRateLimitedOfflineAndOtherHttpFailures() = runBlocking {
        assertEquals(SktjfFetchResult.ReleaseNotFound, client { response(it, 404) }.fetch("2026.S.0", "1-1"))
        assertEquals(SktjfFetchResult.RateLimited, client { response(it, 403, headers = mapOf("X-RateLimit-Remaining" to "0")) }.fetch("2026.S.0", "1-1"))
        assertEquals(SktjfFetchResult.RateLimited, client { response(it, 429) }.fetch("2026.S.0", "1-1"))
        assertEquals(SktjfFetchResult.RateLimited, client { response(it, 403, headers = mapOf("Retry-After" to "60")) }.fetch("2026.S.0", "1-1"))
        assertEquals(SktjfFetchResult.HttpFailure(403), client { response(it, 403) }.fetch("2026.S.0", "1-1"))
        assertEquals(SktjfFetchResult.NetworkFailure, client { throw IOException("offline") }.fetch("2026.S.0", "1-1"))
    }

    @Test fun allowsGithubAssetRedirectAndRejectsOtherDestinations() = runBlocking {
        val urls = mutableListOf<String>()
        val safe = client { chain ->
            val url = chain.request().url.toString(); urls += url
            when {
                url.endsWith("manifest.json") -> response(chain, 302, headers = mapOf("Location" to "https://release-assets.githubusercontent.com/test-manifest"))
                url.endsWith("test-manifest") -> response(chain, 200, manifest)
                else -> response(chain, 200, document)
            }
        }
        assertTrue(safe.fetch("2026.S.0", "1-1") is SktjfFetchResult.Success)
        assertEquals(3, urls.size)
        var count = 0
        val unsafe = client { chain -> count++; response(chain, 302, headers = mapOf("Location" to "https://example.com/unsafe")) }
        assertEquals(SktjfFetchResult.InvalidData, unsafe.fetch("2026.S.0", "1-1"))
        assertEquals(1, count)
    }

    @Test fun rejectsOversizedAndInvalidRequests() = runBlocking {
        var calls = 0
        val oversized = client { chain -> calls++; response(chain, 200, ByteArray(512 * 1024 + 1)) }
        assertEquals(SktjfFetchResult.InvalidData, oversized.fetch("2026.S.0", "1-1"))
        assertEquals(SktjfFetchResult.InvalidData, oversized.fetch("../unsafe", "1-1"))
        assertEquals(1, calls)
        val invalidUtf8 = client { response(it, 200, byteArrayOf(0xc3.toByte(), 0x28)) }
        assertEquals(SktjfFetchResult.InvalidData, invalidUtf8.fetch("2026.S.0", "1-1"))
    }

    @Test fun discoversHighestPublishedRevisionAcrossPages() = runBlocking {
        fun release(tag: String, draft: Boolean = false, prerelease: Boolean = false) =
            """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"published_at":"2026-04-01T00:00:00Z"}"""
        val first = (listOf(release("2026.S.9"), release("2026.S.99", prerelease = true)) +
            (1..28).map { release("unrelated-$it") }).joinToString(",", "[", "]")
        val second = listOf(release("2026.S.12"), release("2026.F.999"), release("2026.S.100", draft = true))
            .joinToString(",", "[", "]")
        val urls = mutableListOf<String>()
        val client = client { chain ->
            urls += chain.request().url.toString()
            response(chain, 200, (if (chain.request().url.queryParameter("page") == "1") first else second).toByteArray())
        }
        assertEquals(SktjfDiscoveryResult.Found("2026.S.12"), client.discover(2026, "S"))
        assertEquals(2, urls.size)
        assertTrue(urls.first().startsWith("https://api.github.com/repos/example/SKTJF/releases?"))
    }

    @Test fun verifiesManifestBeforeAdvertisingUnassignedYear() = runBlocking {
        val valid = client { chain -> response(chain, 200, manifest) }
        assertEquals("2026.S.0", valid.verifiedManifest("2026.S.0")?.revision)
        assertNull(valid.verifiedManifest("2026.S.1"))
        assertNull(client { response(it, 404) }.verifiedManifest("2026.S.0"))
    }

    @Test fun discoveryDistinguishesUnpublishedOfflineRateLimitAndInvalidPayload() = runBlocking {
        assertEquals(SktjfDiscoveryResult.NotPublished, client { response(it, 200, "[]".toByteArray()) }.discover(2026, "S"))
        assertEquals(SktjfDiscoveryResult.HttpFailure(404), client { response(it, 404) }.discover(2026, "S"))
        assertEquals(SktjfDiscoveryResult.RateLimited, client { response(it, 429) }.discover(2026, "S"))
        assertEquals(SktjfDiscoveryResult.NetworkFailure, client { throw IOException("offline") }.discover(2026, "S"))
        assertEquals(SktjfDiscoveryResult.InvalidData, client { response(it, 200, "[{\"tag_name\":\"2026.S.0\"}]".toByteArray()) }.discover(2026, "S"))
        assertEquals(SktjfDiscoveryResult.InvalidData, client { response(it, 200, "[]".toByteArray()) }.discover(2026, "X"))
    }
}
