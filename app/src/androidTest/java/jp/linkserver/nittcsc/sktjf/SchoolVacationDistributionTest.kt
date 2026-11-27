package jp.linkserver.nittcsc.sktjf

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.data.AppDatabase
import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.SchedulerRepository
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.UiDesignPreferences
import jp.linkserver.nittcsc.data.UsageModeEntity
import jp.linkserver.nittcsc.data.getEffectiveDayType
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SchoolVacationDistributionTest {
    @Test fun standaloneCandidateUpdatesTheSameVacationWithExplicitUserChoice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val firstTag = "vacations-2026.F.0.Beta"
        val nextTag = "vacations-2027.S.0.Beta"
        var tags = listOf("2026.F.1.Beta", firstTag)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            val body = if (url.host == "api.github.com") {
                JSONArray(tags.map { tag ->
                    val assets = if (tag.startsWith(VACATION_TAG_PREFIX)) listOf(VACATION_MANIFEST_NAME, VACATION_ASSET_NAME)
                        else listOf("manifest.json", "1-1.sktjf.json")
                    JSONObject().put("tag_name", tag).put("draft", false).put("prerelease", true)
                        .put("published_at", "2026-04-01T00:00:00Z")
                        .put("assets", JSONArray(assets.map { JSONObject().put("name", it) }))
                }).toString().toByteArray()
            } else {
                assertEquals("github.com", url.host)
                assertEquals(listOf("example", "SKTJF", "releases", "download"), url.pathSegments.take(4))
                val tag = url.pathSegments[4]
                assertTrue(tag in listOf(firstTag, nextTag))
                testAssets.open("school-vacations/$tag/${url.pathSegments.last()}").use { it.readBytes() }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val client = SktjfDistributionClient("example/SKTJF", http, beta = true)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            val repo = SchedulerRepository(db, UiDesignPreferences(context))
            val march = LocalDate.parse("2027-03-31")
            val april = march.plusDays(1)
            val id = "2026-year_end"
            dao.upsertUsageMode(UsageModeEntity(mode = SchedulerUsageMode.NITTC.name))
            dao.upsertDayTypes(listOf(DayTypeEntity(march, DayType.A), DayTypeEntity(april, DayType.A)))
            assertEquals(SktjfDiscoveryResult.Found("2026.F.1.Beta"), client.discover(2026, "F"))
            assertEquals(SktjfDiscoveryResult.Found(firstTag), client.discoverVacations(2026))
            val initial = client.fetchVacations(firstTag) as SktjfVacationFetchResult.Success
            val first = repo.previewSchoolVacations(initial.revision, initial.periods)
            assertTrue(dao.getSchoolVacations().isEmpty())
            assertTrue(repo.applySchoolVacations(first, setOf(id)))
            assertEquals(DayType.HOLIDAY, dao.getEffectiveDayType(march)?.dayType)
            assertEquals(DayType.A, dao.getEffectiveDayType(april)?.dayType)
            val original = dao.getSchoolVacations().single()
            assertNull(original.base.end)
            assertEquals(4, original.base.estimatedEndMonth)
            assertEquals(mapOf(2027 to setOf(id)), pendingSchoolVacationIds(listOf(original), 2026))
            assertEquals(SktjfDiscoveryResult.NotPublished, client.discoverVacations(2027))

            assertTrue(repo.editSchoolVacation(original, original.base.copy(name = "手動編集")))
            tags = tags + nextTag
            assertEquals(SktjfDiscoveryResult.NotPublished, client.discover(2027, "S"))
            assertEquals(SktjfDiscoveryResult.Found(nextTag), client.discoverVacations(2027))
            val updated = client.fetchVacations(nextTag) as SktjfVacationFetchResult.Success
            assertEquals(2, updated.periods.single().sources.size)
            val offer = repo.previewSchoolVacations(updated.revision, updated.periods)
            assertTrue(offer.hasNewlyConfirmedDates())
            assertFalse(offer.changes.single().defaultSelected(manual = false))
            assertEquals("手動編集", dao.getSchoolVacations().single().local?.name)
            assertTrue(repo.applySchoolVacations(offer, emptySet()))
            val kept = dao.getSchoolVacations().single()
            assertEquals("手動編集", kept.local?.name)
            assertEquals(LocalDate.parse("2027-04-05"), kept.base.end)
            assertEquals(DayType.A, dao.getEffectiveDayType(april)?.dayType)
            assertTrue(pendingSchoolVacationIds(listOf(kept), 2026).isEmpty())

            val manual = repo.previewSchoolVacations(updated.revision, updated.periods)
            assertTrue(manual.changes.single().defaultSelected(manual = true))
            assertTrue(repo.applySchoolVacations(manual, setOf(id)))
            val final = dao.getSchoolVacations().single()
            assertEquals(id, final.id)
            assertEquals(final.base, final.local)
            assertEquals(DayType.HOLIDAY, dao.getEffectiveDayType(april)?.dayType)
            assertEquals(DayType.A, dao.getDayType(april)?.dayType)
            assertTrue(repo.previewSchoolVacations(updated.revision, updated.periods).changes.isEmpty())
            assertTrue(repo.previewSchoolVacations(initial.revision, initial.periods).changes.isEmpty())
        } finally {
            db.close()
        }
    }
}
