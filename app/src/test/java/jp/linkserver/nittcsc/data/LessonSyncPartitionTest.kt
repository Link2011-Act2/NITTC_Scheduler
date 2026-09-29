package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.logic.TimetableTerm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LessonSyncPartitionTest {
    @Test
    fun `partition keys distinguish academic years and terms`() {
        val first = LessonSyncPartition(2026, TimetableTerm.FIRST).datasetKey
        val second = LessonSyncPartition(2026, TimetableTerm.SECOND).datasetKey
        val nextYear = LessonSyncPartition(2027, TimetableTerm.FIRST).datasetKey
        assertEquals("lessons/2026/FIRST", first)
        assertEquals(LessonSyncPartition(2026, TimetableTerm.SECOND), lessonSyncPartition(second))
        assertEquals(LessonSyncPartition(2027, TimetableTerm.FIRST), lessonSyncPartition(nextYear))
        assertNull(lessonSyncPartition("lessons/2026/THIRD"))
        assertNull(lessonSyncPartition("lessons"))
    }
}
