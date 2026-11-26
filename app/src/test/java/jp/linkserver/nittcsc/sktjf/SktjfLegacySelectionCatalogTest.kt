package jp.linkserver.nittcsc.sktjf

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SktjfLegacySelectionCatalogTest {
    private val source = SktjfReference("src", 1, null)
    private fun candidate(term: String, revision: String, classId: String, day: Int, period: Int,
        cycle: SktjfCycle, ids: List<String>): SktjfV2Candidate {
        val lessons = ids.map { SktjfLesson(it, day, period, cycle, it, emptyList(), emptyList(),
            SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null, listOf(source)) }
        val date = LocalDate.of(2026, 4, 6)
        return SktjfV2Candidate(SktjfDocument(2026, term, date, date, classId, revision, emptyList(), emptyList(),
            emptyList(), lessons, emptyList()), emptyMap(), emptyMap())
    }

    @Test fun onlyExactPublishedAssetsGainDocumentedGroups() {
        val values = listOf(
            Triple("41ae6d38e9090763009157c8c6a2afeb4f3a200564475079378d3d91161d2ed0",
                candidate("S", "2026.S.2.Beta", "4B", 4, 4, SktjfCycle.WEEKLY,
                    listOf("068d9dce-7406-447f-91ea-2fce093fe4c9", "a3d2250b-eb1b-4d93-968d-21554ec9d28e")), "legacy-2026-s-4b-4-4"),
            Triple("dd6a1f5c2e99e31baf0fea5a6152899cb807e7ad7fcacb7a1ee148e13c22d14c",
                candidate("S", "2026.S.2.Beta", "4E", 4, 3, SktjfCycle.WEEKLY,
                    listOf("46204cd0-c13e-4cf3-aaec-663a60e2f2af", "f262cbe0-f2c4-4eaa-b91b-7c66e384de06")), "legacy-2026-s-4e-4-3"),
            Triple("d4c81f50c39b728d16cb73e5b25aed0cf852fa4d1a775b90fa2da9183383baf5",
                candidate("F", "2026.F.1.Beta", "5E", 1, 1, SktjfCycle.A,
                    listOf("28189e85-f0d9-47fa-b69f-d298761a7eb2", "5bc017f2-3ac9-4072-a1a3-114b4fc3938e")), "legacy-2026-f-5e-1-1-a")
        )
        values.forEach { (hash, candidate, groupId) ->
            val adapted = requireNotNull(SktjfLegacySelectionCatalog.adaptVerifiedHash(hash, candidate))
            assertEquals(candidate, adapted.timetable)
            assertEquals(groupId, adapted.selectionGroups.single().id)
            assertEquals(candidate.document.lessons.map { it.id }, adapted.selectionGroups.single().lessonIds)
            assertNull(SktjfLegacySelectionCatalog.adaptVerifiedHash("0".repeat(64), candidate))
            assertNull(SktjfLegacySelectionCatalog.adaptVerifiedHash(hash, candidate.copy(document = candidate.document.copy(revision = "2026.S.9.Beta"))))
            assertNull(SktjfLegacySelectionCatalog.adaptVerifiedHash(hash, candidate.copy(document = candidate.document.copy(lessons = candidate.document.lessons.drop(1)))))
            assertNull(SktjfLegacySelectionCatalog.adapt("different bytes".toByteArray(), candidate))
        }
    }
}
