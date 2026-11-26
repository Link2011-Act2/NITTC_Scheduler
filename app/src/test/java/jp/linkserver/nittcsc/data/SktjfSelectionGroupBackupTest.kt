package jp.linkserver.nittcsc.data

import org.junit.Assert.*
import org.junit.Test

class SktjfSelectionGroupBackupTest {
    private fun group(selected: String? = "a") = SktjfSelectionGroupEntity(2026,"S","2M","group",1,2,
        "[\"a\",\"b\"]", "[{\"sourceId\":\"src\",\"page\":1}]", selected)
    @Test fun preservesSelectedUnknownAndRemovedChoice() {
        for (selected in listOf("a",null,"removed")) {
            val item=group(selected)
            assertEquals(listOf(item), SktjfSelectionGroupBackup.decode(SktjfSelectionGroupBackup.encode(listOf(item))))
        }
    }
    @Test fun rejectsDuplicateGroupIdsAndMalformedSource() {
        val item=group()
        assertTrue(runCatching { SktjfSelectionGroupBackup.encode(listOf(item,item)) }.isFailure)
        assertTrue(runCatching { SktjfSelectionGroupBackup.encode(listOf(item.copy(sourcesJson="[]"))) }.isFailure)
        assertTrue(runCatching { SktjfSelectionGroupBackup.encode(listOf(item.copy(lessonIdsJson="[\"a\",\"a\"]"))) }.isFailure)
    }
    @Test fun retiredIdsRoundTripAndOldBackupsRemainReadable() {
        val item = group("removed").copy(retiredLessonIdsJson = "[\"removed\"]")
        val encoded = SktjfSelectionGroupBackup.encode(listOf(item))
        assertEquals(listOf(item), SktjfSelectionGroupBackup.decode(encoded))
        encoded.getJSONObject(0).remove("retiredLessonIds")
        assertEquals(listOf(item.copy(retiredLessonIdsJson = "[]")), SktjfSelectionGroupBackup.decode(encoded))
        assertTrue(runCatching { SktjfSelectionGroupBackup.encode(listOf(item.copy(retiredLessonIdsJson = "[\"a\"]"))) }.isFailure)
    }
    @Test fun replacementStateRoundTripsAndOldBackupsRemainActive() {
        val item = group().copy(suspendedByTimetableReplacement = true)
        val encoded = SktjfSelectionGroupBackup.encode(listOf(item))
        assertEquals(listOf(item), SktjfSelectionGroupBackup.decode(encoded))
        encoded.getJSONObject(0).remove("suspendedByTimetableReplacement")
        assertEquals(listOf(item.copy(suspendedByTimetableReplacement = false)), SktjfSelectionGroupBackup.decode(encoded))
        encoded.getJSONObject(0).put("suspendedByTimetableReplacement", "true")
        assertTrue(runCatching { SktjfSelectionGroupBackup.decode(encoded) }.isFailure)
    }
}
