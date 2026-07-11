package jp.linkserver.nittcsc.sync

import jp.linkserver.nittcsc.data.SchedulerRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPayloadCoordinatorTest {
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
