package jp.linkserver.nittcsc.sync

import jp.linkserver.nittcsc.data.SchedulerRepository
import jp.linkserver.nittcsc.data.CURRENT_SYNC_PROTOCOL_VERSION
import jp.linkserver.nittcsc.data.SECOND_TERM_START_SYNC_KEY
import jp.linkserver.nittcsc.data.SyncRegisteredDeviceEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPayloadCoordinatorTest {
    @Test
    fun extraLessonDifferenceUsesSamePartitionConflictAndResolution() {
        val key = "lessons/2026/FIRST"
        fun payload(subject: String) = JSONObject().put("syncProtocolVersion", CURRENT_SYNC_PROTOCOL_VERSION)
            .put(SECOND_TERM_START_SYNC_KEY, "10-1").put("device", JSONObject().put("deviceName", "test"))
            .put("metadata", JSONObject().put(key, JSONObject().put("updatedAt", 100)))
            .put(key, JSONArray().put(JSONObject().put("recordType", "sktjf-extra").put("subject", subject)))
        val local = payload("数学"); val remote = payload("英語"); val coordinator = SyncPayloadCoordinator { 200L }
        assertEquals(listOf(key), coordinator.detectConflicts(local, remote, SyncRegisteredDeviceEntity(deviceId = "remote"), true).map { it.datasetKey })
        val result = coordinator.buildMergedPayload(local, remote, "remote", mapOf(key to SyncChoice.REMOTE))
        assertEquals("英語", result.getJSONArray(key).getJSONObject(0).getString("subject"))
        assertEquals("sktjf-extra", result.getJSONArray(key).getJSONObject(0).getString("recordType"))
    }
    @Test
    fun conflictRequiresBothSidesToChangeAfterLastSync() {
        assertTrue(hasSyncConflict(false, 200, 300, 100))
        assertFalse(hasSyncConflict(false, 200, 100, 100))
        assertFalse(hasSyncConflict(false, 100, 300, 100))
    }

    @Test
    fun forcedConflictIgnoresTimestamps() {
        assertTrue(hasSyncConflict(true, 0, 0, 100))
    }

    @Test
    fun explicitResolutionWinsOverTimestamp() {
        val choice = resolveSyncChoice(
            datasetKey = SchedulerRepository.DATASET_TASKS,
            resolutions = mapOf(SchedulerRepository.DATASET_TASKS to SyncChoice.LOCAL),
            localUpdatedAt = 100,
            remoteUpdatedAt = 200
        )

        assertEquals(SyncChoice.LOCAL, choice)
    }

    @Test
    fun cancelledLessonsFollowLessonResolutionWhenNotExplicitlySelected() {
        val choice = resolveSyncChoice(
            datasetKey = SchedulerRepository.DATASET_CANCELLED_LESSONS,
            resolutions = mapOf(SchedulerRepository.DATASET_LESSONS to SyncChoice.REMOTE),
            localUpdatedAt = 300,
            remoteUpdatedAt = 100
        )

        assertEquals(SyncChoice.REMOTE, choice)
    }

    @Test
    fun newerTimestampIsDefaultResolution() {
        assertEquals(
            SyncChoice.REMOTE,
            resolveSyncChoice(SchedulerRepository.DATASET_PLANS, emptyMap(), 100, 200)
        )
        assertEquals(
            SyncChoice.LOCAL,
            resolveSyncChoice(SchedulerRepository.DATASET_PLANS, emptyMap(), 200, 200)
        )
    }
}
