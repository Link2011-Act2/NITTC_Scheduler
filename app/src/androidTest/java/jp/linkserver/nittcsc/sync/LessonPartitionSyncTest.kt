package jp.linkserver.nittcsc.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.linkserver.nittcsc.data.CURRENT_SYNC_PROTOCOL_VERSION
import jp.linkserver.nittcsc.data.LessonSyncPartition
import jp.linkserver.nittcsc.data.SECOND_TERM_START_SYNC_KEY
import jp.linkserver.nittcsc.data.SyncRegisteredDeviceEntity
import jp.linkserver.nittcsc.logic.TimetableTerm
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LessonPartitionSyncTest {
    private val first = LessonSyncPartition(2026, TimetableTerm.FIRST).datasetKey
    private val second = LessonSyncPartition(2026, TimetableTerm.SECOND).datasetKey
    private val nextFirst = LessonSyncPartition(2027, TimetableTerm.FIRST).datasetKey

    private fun payload(vararg entries: Pair<String, String>): JSONObject {
        val root = JSONObject()
            .put("syncProtocolVersion", CURRENT_SYNC_PROTOCOL_VERSION)
            .put(SECOND_TERM_START_SYNC_KEY, "10-1")
            .put("device", JSONObject().put("deviceName", "test"))
        val meta = JSONObject()
        entries.forEach { (key, subject) ->
            root.put(key, JSONArray().put(JSONObject().put("subject", subject)))
            meta.put(key, JSONObject().put("updatedAt", 100L))
        }
        root.put("metadata", meta)
        return root
    }

    @Test
    fun distinctTermEditsMergeWithoutConflictOrLoss() {
        val local = payload(first to "数学")
        val remote = payload(second to "英語", nextFirst to "国語")
        val coordinator = SyncPayloadCoordinator { 200L }
        assertTrue(coordinator.detectConflicts(
            local, remote, SyncRegisteredDeviceEntity(deviceId = "remote"), true
        ).isEmpty())
        val merged = coordinator.buildMergedPayload(local, remote, "remote", emptyMap())
        assertEquals("数学", merged.getJSONArray(first).getJSONObject(0).getString("subject"))
        assertEquals("英語", merged.getJSONArray(second).getJSONObject(0).getString("subject"))
        assertEquals("国語", merged.getJSONArray(nextFirst).getJSONObject(0).getString("subject"))
    }

    @Test
    fun sameYearAndTermDifferenceIsASeparateConflict() {
        val coordinator = SyncPayloadCoordinator()
        val conflicts = coordinator.detectConflicts(
            payload(first to "数学", second to "英語"),
            payload(first to "理科", second to "英語"),
            SyncRegisteredDeviceEntity(deviceId = "remote"), true
        )
        assertEquals(listOf(first), conflicts.map { it.datasetKey })
    }

    @Test
    fun firstAndSecondTermConflictsCanBeResolvedIndependently() {
        val coordinator = SyncPayloadCoordinator { 200L }
        val local = payload(first to "数学", second to "")
        val remote = payload(first to "", second to "英語")
        val merged = coordinator.buildMergedPayload(
            local, remote, "remote",
            mapOf(first to SyncChoice.LOCAL, second to SyncChoice.REMOTE)
        )
        assertEquals("数学", merged.getJSONArray(first).getJSONObject(0).getString("subject"))
        assertEquals("英語", merged.getJSONArray(second).getJSONObject(0).getString("subject"))
    }
}
