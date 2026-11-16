package jp.linkserver.nittcsc.sktjf

import org.junit.Assert.*
import org.junit.Test

class SktjfMergePlannerTest {
    private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/sktjf/$name")).use { it.readBytes() }
    private fun document(): SktjfDocument {
        val manifest = SktjfJson.manifest(resource("synthetic-manifest-v1.json"), "example/SKTJF")
        return SktjfJson.verifiedDocument(resource("synthetic-v1.json"), manifest, manifest.assets.single())
    }
    private fun baseline(doc: SktjfDocument) = SktjfBaseline(doc.academicYear, doc.term, doc.classId, doc.lessons.associate { it.id to it.content() })
    private fun partition(doc: SktjfDocument, rows: List<SktjfLocalLesson>) = SktjfLocalTimetable(doc.academicYear, doc.term, doc.classId, rows)
    private inline fun rejects(action: () -> Unit) { assertTrue(runCatching(action).isFailure) }

    @Test fun updatesUneditedSourceWithoutChangingLocalIdentity() {
        val old = document(); val lesson = old.lessons.single(); val local = SktjfLocalLesson("lesson:71:A", lesson.id, lesson.content())
        val next = old.copy(revision = "2026.S.1", lessons = listOf(lesson.copy(subject = "新しい科目")))
        val plan = SktjfLessonMergePlan.create(baseline(old), partition(next, listOf(local)), next)
        assertTrue(plan.conflicts.isEmpty())
        val result = plan.resolve(emptyMap()).lessons.single()
        assertEquals("lesson:71:A", result.localKey); assertEquals(lesson.id, result.sourceLessonId)
        assertEquals("新しい科目", result.content.subject)
    }

    @Test fun profileVariantReplacesOnlyUneditedSourceAndPreservesIdentity() {
        val old = document(); val lesson = old.lessons.single()
        val local = SktjfLocalLesson("lesson:71:A", lesson.id, lesson.content())
        val next = old.copy(lessons = listOf(lesson.copy(id = "other-profile", subject = "別の所属の授業")))
        val plan = SktjfLessonMergePlan.create(baseline(old), partition(next, listOf(local)), next)
        assertTrue(plan.conflicts.isEmpty())
        val result = plan.resolve(emptyMap()).lessons.single()
        assertEquals(local.localKey, result.localKey)
        assertEquals("other-profile", result.sourceLessonId)
        assertEquals("別の所属の授業", result.content.subject)
        val edited = local.copy(content = local.content.copy(subject = "手動変更"))
        val conflict = SktjfLessonMergePlan.create(baseline(old), partition(next, listOf(edited)), next)
        assertEquals(SktjfConflictReason.BOTH_CHANGED, conflict.conflicts.single().reason)
        assertEquals("手動変更", conflict.resolve(mapOf("other-profile" to SktjfConflictChoice.KEEP_LOCAL)).lessons.single().content.subject)
    }

    @Test fun concurrentEditsAndLocalDeletionRequireExplicitChoice() {
        val old = document(); val lesson = old.lessons.single(); val next = old.copy(lessons = listOf(lesson.copy(subject = "配信版")))
        val local = SktjfLocalLesson("lesson:71:A", lesson.id, lesson.content().copy(subject = "手動版"))
        val plan = SktjfLessonMergePlan.create(baseline(old), partition(next, listOf(local)), next)
        assertEquals(SktjfConflictReason.BOTH_CHANGED, plan.conflicts.single().reason)
        rejects { plan.resolve(emptyMap()) }
        assertEquals("手動版", plan.resolve(mapOf(lesson.id to SktjfConflictChoice.KEEP_LOCAL)).lessons.single().content.subject)
        val applied = plan.resolve(mapOf(lesson.id to SktjfConflictChoice.APPLY_INCOMING)).lessons.single()
        assertEquals("lesson:71:A", applied.localKey); assertEquals("配信版", applied.content.subject)
        val deleted = SktjfLessonMergePlan.create(baseline(old), partition(next, emptyList()), next)
        assertEquals(SktjfConflictReason.BOTH_CHANGED, deleted.conflicts.single().reason)
        assertTrue(deleted.resolve(mapOf(lesson.id to SktjfConflictChoice.KEEP_LOCAL)).lessons.isEmpty())
        assertNull(deleted.resolve(mapOf(lesson.id to SktjfConflictChoice.APPLY_INCOMING)).lessons.single().localKey)
    }

    @Test fun deletedSourceIsRemovedButManualAndExcludedLessonsAreProtected() {
        val old = document(); val lesson = old.lessons.single()
        val locals = listOf(SktjfLocalLesson("source", lesson.id, lesson.content()), SktjfLocalLesson("manual", null, lesson.content().copy(subject = "手動追加")))
        for (state in listOf(SktjfSlotState.EMPTY, SktjfSlotState.EXCLUDED)) {
            val next = old.copy(lessons = emptyList(), slotCoverage = old.slotCoverage.map { if (it.dayOfWeek == 1) it.copy(state = state) else it })
            val plan = SktjfLessonMergePlan.create(baseline(old), partition(next, locals), next)
            val resolved = plan.resolve(emptyMap())
            if (state == SktjfSlotState.EMPTY) {
                assertEquals(setOf("manual"), resolved.lessons.mapNotNull { it.localKey }.toSet())
                assertEquals(setOf("source"), resolved.clearedLocalKeys)
                assertNull(resolved.baseline.lessons[lesson.id])
            } else {
                assertEquals(setOf("source", "manual"), resolved.lessons.mapNotNull { it.localKey }.toSet())
                assertEquals(lesson.content(), resolved.baseline.lessons[lesson.id])
            }
        }
    }

    @Test fun deletedSourceWithLocalEditRequiresChoiceAndKeepUnlinksIt() {
        val old = document(); val lesson = old.lessons.single()
        val local = SktjfLocalLesson("source", lesson.id, lesson.content().copy(subject = "手動編集"))
        val next = old.copy(lessons = emptyList(), slotCoverage = old.slotCoverage.map { it.copy(state = SktjfSlotState.EMPTY) })
        val plan = SktjfLessonMergePlan.create(baseline(old), partition(next, listOf(local)), next)
        assertTrue(plan.conflicts.single().isDeletion)
        val kept = plan.resolve(mapOf(lesson.id to SktjfConflictChoice.KEEP_LOCAL))
        assertEquals("手動編集", kept.lessons.single().content.subject)
        assertNull(kept.lessons.single().sourceLessonId)
        assertNull(kept.baseline.lessons[lesson.id])
        val removed = plan.resolve(mapOf(lesson.id to SktjfConflictChoice.APPLY_INCOMING))
        assertTrue(removed.lessons.isEmpty()); assertEquals(setOf("source"), removed.clearedLocalKeys)
    }

    @Test fun overlappingManualLessonIsNeverSilentlyReplaced() {
        val next = document(); val lesson = next.lessons.single()
        val manual = SktjfLocalLesson("manual:1", null, lesson.content().copy(subject = "手動追加"))
        val plan = SktjfLessonMergePlan.create(null, partition(next, listOf(manual)), next)
        assertEquals(SktjfConflictReason.OVERLAPPING_LOCAL, plan.conflicts.single().reason)
        assertEquals(listOf("manual:1"), plan.resolve(mapOf(lesson.id to SktjfConflictChoice.KEEP_LOCAL)).lessons.map { it.localKey })
        val replaced = plan.resolve(mapOf(lesson.id to SktjfConflictChoice.APPLY_INCOMING)).lessons
        assertEquals(1, replaced.size); assertEquals("manual:1", replaced.single().localKey)
        assertEquals(lesson.id, replaced.single().sourceLessonId)
        assertEquals(lesson.subject, replaced.single().content.subject)
    }

    @Test fun replacingWeeklyWithAlternatingKeepsRowIdentityAndUnselectedWeek() {
        val old = document(); val lesson = old.lessons.single()
        val weekly = lesson.copy(cycle = SktjfCycle.WEEKLY)
        val local = SktjfLocalLesson("base:71:WEEKLY", null, weekly.content().copy(subject = "手動授業"))
        val a = lesson.copy(id = "a", cycle = SktjfCycle.A, subject = "A授業")
        val b = lesson.copy(id = "b", cycle = SktjfCycle.B, subject = "B授業")
        val next = old.copy(lessons = listOf(a, b))
        val plan = SktjfLessonMergePlan.create(null, partition(next, listOf(local)), next)
        assertEquals(2, plan.conflicts.size)
        val both = plan.resolve(mapOf("a" to SktjfConflictChoice.APPLY_INCOMING, "b" to SktjfConflictChoice.APPLY_INCOMING)).lessons
        assertEquals(setOf("A授業", "B授業"), both.map { it.content.subject }.toSet())
        assertEquals(1, both.count { it.localKey == local.localKey })
        val partial = plan.resolve(mapOf("a" to SktjfConflictChoice.APPLY_INCOMING, "b" to SktjfConflictChoice.KEEP_LOCAL)).lessons
        assertEquals("A授業", partial.single { it.content.cycle == SktjfCycle.A }.content.subject)
        assertEquals("手動授業", partial.single { it.content.cycle == SktjfCycle.B }.content.subject)
    }

    @Test fun replacingBothAlternatingWeeksWithWeeklyRetainsRowIdentity() {
        val old = document(); val lesson = old.lessons.single()
        val a = SktjfLocalLesson("base:71:A", null, lesson.content().copy(cycle = SktjfCycle.A))
        val b = SktjfLocalLesson("base:71:B", null, lesson.content().copy(cycle = SktjfCycle.B))
        val next = old.copy(lessons = listOf(lesson.copy(cycle = SktjfCycle.WEEKLY)))
        val plan = SktjfLessonMergePlan.create(null, partition(next, listOf(a, b)), next)
        val merged = plan.resolve(mapOf(lesson.id to SktjfConflictChoice.APPLY_INCOMING))
        assertEquals(1, merged.lessons.size)
        assertEquals("base:71:A", merged.lessons.single().localKey)
        assertEquals(setOf("base:71:B"), merged.replacedLocalKeys)
        assertEquals(SktjfCycle.WEEKLY, merged.lessons.single().content.cycle)
    }

    @Test fun mutuallyExclusiveAttributesAndDifferentCyclesCoexist() {
        val next = document(); val lesson = next.lessons.single()
        val notDd = SktjfLocalLesson("manual:1", null, lesson.content().copy(conditions = SktjfConditions(emptySet(), setOf("DD"))))
        val otherCycle = SktjfLocalLesson("manual:2", null, lesson.content().copy(cycle = SktjfCycle.B))
        val plan = SktjfLessonMergePlan.create(null, partition(next, listOf(notDd, otherCycle)), next)
        assertTrue(plan.conflicts.isEmpty()); assertEquals(3, plan.resolve(emptyMap()).lessons.size)
    }

    @Test fun simultaneousDistributedLessonsAreNotCollapsed() {
        val old = document(); val a = old.lessons.single()
        val b = a.copy(id = "second", subject = "別の授業", conditions = SktjfConditions(emptySet(), setOf("DD")))
        val plan = SktjfLessonMergePlan.create(null, partition(old, emptyList()), old.copy(lessons = listOf(a, b)))
        assertTrue(plan.conflicts.isEmpty()); assertEquals(setOf(a.id, b.id), plan.resolve(emptyMap()).lessons.map { it.sourceLessonId }.toSet())
    }

    @Test fun unchangedDistributionKeepsLocalEditAndPlanIsAnIndependentSnapshot() {
        val doc = document(); val lesson = doc.lessons.single(); val teachers = mutableListOf("手動の先生")
        val local = SktjfLocalLesson("lesson:71:A", lesson.id, lesson.content().copy(subject = "手動版", teachers = teachers))
        val plan = SktjfLessonMergePlan.create(baseline(doc), partition(doc, listOf(local)), doc)
        teachers += "後から追加"
        assertTrue(plan.conflicts.isEmpty())
        assertEquals("手動版", plan.resolve(emptyMap()).lessons.single().content.subject)
        assertEquals(listOf("手動の先生"), plan.resolve(emptyMap()).lessons.single().content.teachers)
    }

    @Test fun rejectsWrongProfileDuplicateSourcesAndLostBaseline() {
        val doc = document(); val lesson = doc.lessons.single(); val local = SktjfLocalLesson("a", lesson.id, lesson.content())
        rejects { SktjfLessonMergePlan.create(baseline(doc).copy(classId = "1-2"), partition(doc, listOf(local)), doc) }
        rejects { SktjfLessonMergePlan.create(baseline(doc), partition(doc, listOf(local, local.copy(localKey = "b"))), doc) }
        rejects { SktjfLessonMergePlan.create(null, partition(doc, listOf(local)).copy(academicYear = 2025), doc) }
        val plan = SktjfLessonMergePlan.create(null, partition(doc, listOf(local)), doc)
        assertEquals(SktjfConflictReason.BASELINE_MISSING, plan.conflicts.single().reason)
    }
}
