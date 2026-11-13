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
}
