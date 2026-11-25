package jp.linkserver.nittcsc.sktjf

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import jp.linkserver.nittcsc.data.SktjfSourceStateBackup
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.data.NittcProfileEntity
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.EffectiveSchoolSchedule
import jp.linkserver.nittcsc.data.lessonKey
import jp.linkserver.nittcsc.widget.WidgetDataHelper
import jp.linkserver.nittcsc.logic.TimetableTerm

class SktjfV2CandidateJsonTest {
    private fun candidate(): JSONObject {
        val json = JSONObject(requireNotNull(javaClass.getResourceAsStream("/sktjf/synthetic-v1.json")).bufferedReader().use { it.readText() })
        json.put("schemaVersion", 2)
        json.getJSONArray("lessons").getJSONObject(0).put("activeWeekRange", JSONObject()
            .put("start", 9).put("end", JSONObject.NULL).put("notation", "（9週〜）"))
        json.put("weekCalendar", JSONArray().put(JSONObject().put("date", "2026-04-06").put("week", 9)
            .put("sources", JSONArray().put(JSONObject().put("sourceId", "src").put("page", 1)))))
        return json
    }
    private fun parse(json: JSONObject) = SktjfV2CandidateJson.parse(json.toString().toByteArray())

    @Test fun `preserves explicit week range and evidence while v1 flow rejects v2`() {
        val json = candidate(); val result = parse(json)
        assertEquals(9, result.ranges.values.single().range.start)
        assertEquals("（9週〜）", result.ranges.values.single().notation)
        assertEquals(9, result.weeks.getValue(LocalDate.of(2026, 4, 6)).number)
        assertTrue(runCatching { SktjfJson.localDocument(json.toString().byteInputStream()) }.isFailure)
    }

    @Test fun `reads the candidate exported by the SKTJF tool`() {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/sktjf/synthetic-v2-week.json")).use { it.readBytes() }
        val result = SktjfV2CandidateJson.parse(bytes)
        assertEquals("卒研", result.document.lessons.single().subject)
        assertEquals(9, result.ranges.values.single().range.start)
        assertEquals(9, result.weeks.getValue(LocalDate.of(2026, 4, 6)).number)
        val baseline=result.sourceBaseline()
        assertEquals(result.ranges.values.single(),baseline.lessons.lessons.values.single().weekRange)
        assertEquals(result.weeks,baseline.weeks)
        assertEquals(baseline,SktjfBaselineCodec.decode(SktjfBaselineCodec.encode(baseline)))
        val selected=result.forProfile(setOf("DD"))
        assertEquals(9,selected.documentForMerge().lessons.single().weekRange?.range?.start)
        assertTrue(selected.documentForMerge().lessons.single().conditions.all.isEmpty())
        assertTrue(result.forProfile(emptySet()).documentForMerge().lessons.isEmpty())
    }

    @Test fun `rejects missing weeks invalid references and unsafe overlap`() {
        val absent = candidate().put("weekCalendar", JSONArray())
        assertTrue(runCatching { parse(absent) }.isFailure)
        val badSource = candidate(); badSource.getJSONArray("weekCalendar").getJSONObject(0)
            .getJSONArray("sources").getJSONObject(0).put("sourceId", "missing")
        assertTrue(runCatching { parse(badSource) }.isFailure)
        val overlap = candidate(); val lessons = overlap.getJSONArray("lessons")
        val another = JSONObject(lessons.getJSONObject(0).toString()).put("id", "second")
        another.remove("activeWeekRange"); lessons.put(another)
        assertTrue(runCatching { parse(overlap) }.isFailure)
    }

    @Test fun `weekly overlap on event day without AB is rejected`() {
        val json = candidate()
        val lessons = json.getJSONArray("lessons")
        val first = lessons.getJSONObject(0).put("cycle", "WEEKLY")
        val day = json.getJSONArray("calendar").getJSONObject(0)
        day.put("dayType", JSONObject.NULL)
        day.put("event", JSONObject().put("name", "行事").put("kind", "EVENT"))
        assertTrue(runCatching { parse(json) }.isSuccess)
        lessons.put(JSONObject(first.toString()).put("id", "second"))
        assertTrue(runCatching { parse(json) }.isFailure)
    }

    @Test fun `Sunday lesson without week evidence is rejected`() {
        val json = candidate()
        json.getJSONObject("term").put("end", "2026-04-12")
        val lessons = json.getJSONArray("lessons")
        lessons.put(JSONObject(lessons.getJSONObject(0).toString()).put("id", "sunday")
            .put("dayOfWeek", 7).put("cycle", "WEEKLY"))
        val coverage = json.getJSONArray("slotCoverage")
        coverage.put(JSONObject(coverage.getJSONObject(0).toString()).put("dayOfWeek", 7))
        val calendar = json.getJSONArray("calendar")
        calendar.put(JSONObject(calendar.getJSONObject(0).toString()).put("date", "2026-04-12"))
        assertTrue(runCatching { parse(json) }.isFailure)
        json.getJSONArray("weekCalendar").put(JSONObject().put("date", "2026-04-12").put("week", 9)
            .put("sources", JSONArray().put(JSONObject().put("sourceId", "src").put("page", 1))))
        assertTrue(runCatching { parse(json) }.isSuccess)
    }

    @Test fun `explicit series preserves the shared slot and rejects invalid membership`() {
        val json = candidate()
        val lessonId = json.getJSONArray("lessons").getJSONObject(0).getString("id")
        val series = JSONObject().put("id", "shared").put("dayOfWeek", 1).put("periods", JSONArray().put(1))
            .put("lessonIds", JSONArray().put(lessonId)).put("cycle", "WEEKLY")
            .put("sources", JSONArray().put(JSONObject().put("sourceId", "src").put("page", 1)))
        json.put("weekSeries", JSONArray().put(series))
        val result = parse(json)
        assertEquals(setOf(1), result.series.single().periods)
        assertEquals(setOf(lessonId), result.series.single().lessonIds)
        val snapshot = result.sourceBaseline()
        assertEquals(result.series, snapshot.series)
        assertEquals(snapshot, SktjfBaselineCodec.decode(SktjfBaselineCodec.encode(snapshot)))
        series.getJSONArray("periods").put(2)
        assertTrue(runCatching { parse(json) }.isFailure)
    }

    @Test fun `profile filtering keeps the full verified opportunity periods in the saved snapshot`() {
        val original = parse(candidate())
        val first = original.document.lessons.single().copy(id = "dd", period = 1,
            conditions = SktjfConditions(setOf("DD"), emptySet()))
        val second = first.copy(id = "other", period = 2, conditions = SktjfConditions(emptySet(), setOf("DD")))
        val group = SktjfV2WeekSeries("shared", first.dayOfWeek, setOf(1, 2), setOf("dd", "other"),
            SktjfCycle.WEEKLY, listOf(SktjfReference("src", 1, null)))
        val candidate = original.copy(document = original.document.copy(lessons = listOf(first, second)),
            ranges = mapOf("dd" to original.ranges.values.single(), "other" to original.ranges.values.single()), series = listOf(group))
        val filtered = candidate.forProfile(setOf("DD"))
        assertEquals(setOf("dd"), filtered.series.single().lessonIds)
        assertEquals(setOf(1, 2), filtered.series.single().periods)
        val baseline = filtered.sourceBaseline()
        assertEquals(baseline, SktjfBaselineCodec.decode(SktjfBaselineCodec.encode(baseline)))
    }

    @Test fun `a single timed lesson ends after the tenth official opportunity`() {
        val json = candidate()
        val start = LocalDate.of(2026, 4, 6)
        json.getJSONObject("term").put("end", start.plusDays(76).toString())
        val days = JSONArray()
        for (offset in 0L..76L) if (start.plusDays(offset).dayOfWeek.value < 6)
            days.put(JSONObject().put("date", start.plusDays(offset).toString()).put("dayType", "A")
                .put("sources", JSONArray().put(JSONObject().put("sourceId", "src").put("page", 1))))
        json.put("calendar", days).put("weekCalendar", JSONArray())
        val lesson = json.getJSONArray("lessons").getJSONObject(0).put("cycle", "WEEKLY")
        lesson.put("activeWeekRange", JSONObject().put("start", JSONObject.NULL).put("end", 10).put("notation", "10週まで"))
        val policy = SktjfCountingPolicy(true, false, emptyMap(), true, sources = listOf(SktjfReference("src", 1, null)))
        json.put("weekSeries", JSONArray().put(JSONObject().put("id", "experiment").put("dayOfWeek", 1)
            .put("periods", JSONArray().put(1)).put("lessonIds", JSONArray().put(lesson.getString("id")))
            .put("cycle", "WEEKLY").put("sources", JSONArray().put(JSONObject().put("sourceId", "src").put("page", 1)))
            .put("countingPolicy", SktjfCountingPolicyJson.encode(policy))))
        val candidate = parse(json)
        val tenth = resolveSktjfV2Opportunity(candidate, "experiment", 1, start.plusWeeks(9), SktjfCycle.A, setOf("DD"))
        val eleventh = resolveSktjfV2Opportunity(candidate, "experiment", 1, start.plusWeeks(10), SktjfCycle.A, setOf("DD"))
        assertEquals(lesson.getString("id"), (tenth as SktjfWeekSelection.Selected).value.id)
        assertEquals(SktjfWeekSelection.Empty, eleventh)
        val saturday = start.plusDays(5)
        val uncertainPolicy = policy.copy(officialPeriodOverrides = mapOf(saturday to mapOf(0 to true)))
        json.getJSONArray("weekSeries").getJSONObject(0).put("countingPolicy", SktjfCountingPolicyJson.encode(uncertainPolicy))
        assertTrue(runCatching { parse(json) }.isFailure)
    }

    @Test fun `inspection can evaluate an explicit series without precomputed dates`() {
        val json = candidate()
        val start = LocalDate.of(2026, 4, 6); val end = start.plusDays(62)
        json.getJSONObject("term").put("end", end.toString())
        val calendar = JSONArray()
        for (offset in 0L..62L) calendar.put(JSONObject().put("date", start.plusDays(offset).toString()).put("dayType", "A")
            .put("sources", JSONArray().put(JSONObject().put("sourceId", "src").put("page", 1))))
        json.put("calendar", calendar).put("weekCalendar", JSONArray())
        val lessons = json.getJSONArray("lessons")
        val before = lessons.getJSONObject(0).put("cycle", "WEEKLY")
        before.put("activeWeekRange", JSONObject().put("start", JSONObject.NULL).put("end", 8).put("notation", "8週まで"))
        val after = JSONObject(before.toString()).put("id", "after").put("subject", "卒研")
            .put("activeWeekRange", JSONObject().put("start", 9).put("end", JSONObject.NULL).put("notation", "9週から"))
        lessons.put(after)
        val beforeId = before.getString("id")
        val policy = SktjfCountingPolicy(regularDayCounts = true, holidayCounts = false, eventCounts = emptyMap(),
            ordinaryPeriodCounts = true, sources = listOf(SktjfReference("src", 1, null)))
        json.put("weekSeries", JSONArray().put(JSONObject().put("id", "shared").put("dayOfWeek", 1)
            .put("periods", JSONArray().put(1)).put("lessonIds", JSONArray().put(beforeId).put("after"))
            .put("cycle", "WEEKLY").put("sources", JSONArray().put(JSONObject().put("sourceId", "src").put("page", 1)))
            .put("countingPolicy", SktjfCountingPolicyJson.encode(policy))))
        val bytes = json.toString().toByteArray()
        assertTrue(runCatching { SktjfV2CandidateJson.parse(bytes) }.isSuccess)
        val candidate = SktjfV2CandidateJson.inspect(bytes)
        assertTrue(candidate.weekEvidenceComplete)
        val prepared = prepareSktjfV2Merge(candidate, setOf("DD"), emptyList(), emptyList(), null) as SktjfV2MergePreparation.Ready
        val proposed = prepared.lessons.resolve(emptyMap())
        assertEquals(setOf(beforeId, "after"), proposed.lessons.mapNotNull { it.sourceLessonId }.toSet())
        assertEquals(setOf(8, 9), proposed.lessons.mapNotNull { it.content.weekRange?.range?.let { range -> range.start ?: range.end } }.toSet())
        val draft = requireNotNull(draftSktjfTimedStorage(prepared.candidate, emptyList(), emptyList(), proposed,
            prepared.candidate.sourceBaseline().calendar) { "alternate" })
        assertEquals("after", draft.alternatives.single().sourceLessonId)
        assertEquals(9, SktjfBaselineCodec.decode(draft.alternatives.single().contentJson).lessons.lessons.getValue("alternate").weekRange?.range?.start)
        assertEquals(8, draft.source.lessons.lessons.getValue(beforeId).weekRange?.range?.end)
        val edited = proposed.lessons.map { if (it.sourceLessonId == beforeId) it.copy(localKey = sktjfLegacyKey(71, SktjfCycle.WEEKLY),
            content = it.content.copy(subject = "利用者の編集")) else it }
        val row = LessonEntity(id = 71, academicYear = 2026, timetableTerm = TimetableTerm.FIRST, dayOfWeek = 1, slotIndex = 0,
            mode = LessonMode.WEEKLY, weeklySubject = "利用者の編集", weeklyTeacher = "", aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")
        val editedDraft = requireNotNull(draftSktjfTimedStorage(prepared.candidate, listOf(row), emptyList(), proposed.copy(lessons = edited),
            prepared.candidate.sourceBaseline().calendar) { "alternate" })
        assertEquals(71L, editedDraft.ordinary.rows.single().id)
        assertEquals("利用者の編集", editedDraft.ordinary.rows.single().weeklySubject)
        val displayedBefore = resolveSktjfTimedStorageDraft(editedDraft, "shared", 1, start.plusWeeks(7), SktjfCycle.A, setOf("DD"))
        val displayedAfter = resolveSktjfTimedStorageDraft(editedDraft, "shared", 1, start.plusWeeks(8), SktjfCycle.A, setOf("DD"))
        assertEquals("利用者の編集", (displayedBefore as SktjfWeekSelection.Selected).value.content.subject)
        assertEquals("after", (displayedAfter as SktjfWeekSelection.Selected).value.sourceLessonId)
        val links = SktjfSourceLinks.encode(listOf(SktjfSourceLink(beforeId, 1, 1, SktjfCycle.WEEKLY)))
        val persisted = SktjfSourceStateEntity(2026, "S", "1-1", "2026.S.0.Beta",
            SktjfBaselineCodec.encode(editedDraft.source), false, links)
        val reconstructed = requireNotNull(restoreSktjfTimedStorage(persisted, editedDraft.ordinary.rows, editedDraft.alternatives))
        assertEquals("利用者の編集", (resolveSktjfTimedStorageDraft(reconstructed, "shared", 1, start.plusWeeks(7),
            SktjfCycle.A, setOf("DD")) as SktjfWeekSelection.Selected).value.content.subject)
        assertEquals("after", (resolveSktjfTimedStorageDraft(reconstructed, "shared", 1, start.plusWeeks(8),
            SktjfCycle.A, setOf("DD")) as SktjfWeekSelection.Selected).value.sourceLessonId)
        assertEquals(null, restoreSktjfTimedStorage(persisted.copy(requiresRelinking = true), editedDraft.ordinary.rows, editedDraft.alternatives))
        val profile = NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", true, false)
        val timedSchedule = SktjfTimedSchedule.create(SchedulerUsageMode.NITTC, listOf(profile), listOf(persisted),
            editedDraft.ordinary.rows, editedDraft.alternatives)
        val schedule = EffectiveSchoolSchedule(SettingsEntity(termStart = start, termEnd = end, enableSemesterTimetables = true),
            emptyMap(), editedDraft.ordinary.rows, emptyMap(), setOf(start.plusWeeks(1) to 0), emptyMap(), emptyMap(),
            null, SchedulerUsageMode.NITTC, null, timedSchedule)
        assertEquals("利用者の編集", schedule.lessonsOn(start.plusWeeks(7)).single().content.subject)
        assertEquals("卒研", schedule.lessonsOn(start.plusWeeks(8)).single().content.subject)
        assertEquals("卒研", schedule.notificationLessonsOn(start.plusWeeks(8)).single().content.subject)
        assertTrue(schedule.lessonsOn(start.plusWeeks(1)).isEmpty())
        fun widget(day: LocalDate) = WidgetDataHelper.resolveLesson(day, 0, editedDraft.ordinary.rows.associateBy { it.lessonKey() },
            emptyMap(), emptyMap(), semesterTimetablesEnabled = true, timedSchedule = timedSchedule)
        assertEquals("利用者の編集", widget(start.plusWeeks(7))?.subject)
        assertEquals(71L, widget(start.plusWeeks(7))?.lessonId)
        assertEquals("卒研", widget(start.plusWeeks(8))?.subject)
        assertEquals("alternate", widget(start.plusWeeks(8))?.extraLessonId)
        val specialSource = editedDraft.source.let { value -> value.copy(lessons = value.lessons.copy(lessons = value.lessons.lessons +
            ("after" to value.lessons.lessons.getValue("after").copy(subject = "補講", kind = SktjfLessonKind.MAKEUP)))) }
        val specialExtra = editedDraft.alternatives.single().let { extra ->
            val value = SktjfBaselineCodec.decode(extra.contentJson)
            extra.copy(contentJson = SktjfBaselineCodec.encode(value.copy(lessons = value.lessons.copy(lessons = mapOf(extra.id to
                value.lessons.lessons.getValue(extra.id).copy(subject = "補講", kind = SktjfLessonKind.MAKEUP))))))
        }
        val specialState = persisted.copy(baselineJson = SktjfBaselineCodec.encode(specialSource))
        val specialTimed = SktjfTimedSchedule.create(SchedulerUsageMode.NITTC, listOf(profile), listOf(specialState),
            editedDraft.ordinary.rows, listOf(specialExtra))
        val specialSchedule = EffectiveSchoolSchedule(SettingsEntity(termStart = start, termEnd = end, enableSemesterTimetables = true),
            emptyMap(), editedDraft.ordinary.rows, emptyMap(), emptySet(), emptyMap(), emptyMap(), null, SchedulerUsageMode.NITTC, null, specialTimed)
        assertEquals("補講", specialSchedule.lessonsOn(start.plusWeeks(8)).single().content.subject)
        assertTrue(specialSchedule.notificationLessonsOn(start.plusWeeks(8)).isEmpty())
        val reedited = edited.map { if (it.sourceLessonId == "after") it.copy(localKey = "extra:alternate",
            content = it.content.copy(subject = "利用者が変更した卒研")) else it }
        val repeated = requireNotNull(draftSktjfTimedStorage(prepared.candidate, listOf(row), editedDraft.alternatives,
            proposed.copy(lessons = reedited), prepared.candidate.sourceBaseline().calendar))
        assertEquals("alternate", repeated.alternatives.single().id)
        assertEquals("利用者が変更した卒研", SktjfBaselineCodec.decode(repeated.alternatives.single().contentJson)
            .lessons.lessons.getValue("alternate").subject)
        assertEquals(null, draftSktjfTimedStorage(prepared.candidate.copy(series = emptyList()), emptyList(), emptyList(),
            proposed, prepared.candidate.sourceBaseline().calendar))
        assertEquals(SktjfV2MergePreparation.Blocked, prepareSktjfV2Merge(candidate.copy(weekEvidenceComplete = false),
            setOf("DD"), emptyList(), emptyList(), null))
        val incomplete = JSONObject(json.toString())
        incomplete.getJSONArray("calendar").getJSONObject(7).put("dayType", JSONObject.NULL)
            .put("event", JSONObject().put("name", "行事").put("kind", "EVENT"))
        assertTrue(runCatching { parse(incomplete) }.isFailure)
        assertTrue(!SktjfV2CandidateJson.inspect(incomplete.toString().toByteArray()).weekEvidenceComplete)
        val collision = JSONObject(json.toString())
        collision.getJSONArray("lessons").getJSONObject(1).getJSONObject("activeWeekRange").put("start", 8)
        assertTrue(runCatching { parse(collision) }.isFailure)
        val holidayCollision = JSONObject(json.toString())
        holidayCollision.getJSONArray("calendar").getJSONObject(0).put("dayType", "HOLIDAY")
        holidayCollision.getJSONArray("weekSeries").getJSONObject(0).getJSONObject("countingPolicy").put("holidayCounts", true)
        holidayCollision.getJSONArray("lessons").getJSONObject(0).getJSONObject("activeWeekRange").put("end", 1)
        holidayCollision.getJSONArray("lessons").getJSONObject(1).getJSONObject("activeWeekRange").put("start", 1).put("end", 1)
        assertTrue(runCatching { parse(holidayCollision) }.isFailure)
        val separateWeek = JSONObject(json.toString())
        separateWeek.getJSONArray("weekCalendar").put(JSONObject().put("date", start.toString()).put("week", 9)
            .put("sources", JSONArray().put(JSONObject().put("sourceId", "src").put("page", 1))))
        val separate = parse(separateWeek)
        assertEquals(beforeId, (resolveSktjfV2Opportunity(separate, "shared", 1, start, SktjfCycle.A, setOf("DD")) as SktjfWeekSelection.Selected).value.id)
        val baseline = candidate.sourceBaseline()
        assertEquals(baseline, SktjfBaselineCodec.decode(SktjfBaselineCodec.encode(baseline)))
        assertEquals(policy, candidate.series.single().countingPolicy)
        fun pick(date: LocalDate) = resolveSktjfV2Opportunity(candidate, "shared", 1, date, SktjfCycle.A, setOf("DD"))
        assertEquals(beforeId, (pick(start.plusWeeks(7)) as SktjfWeekSelection.Selected).value.id)
        assertEquals("after", (pick(start.plusWeeks(8)) as SktjfWeekSelection.Selected).value.id)
        val restored = SktjfBaselineCodec.decode(SktjfBaselineCodec.encode(baseline))
        assertEquals(SktjfTermBounds(start, end), restored.termBounds)
        val state = SktjfSourceStateEntity(2026, "S", "1-1", "2026.S.0.Beta", SktjfBaselineCodec.encode(baseline), false)
        val backup = SktjfSourceStateBackup.encode(listOf(state))
        val restoredState = SktjfSourceStateBackup.decode(backup, preserveLinkage = true).single()
        assertEquals(baseline, SktjfBaselineCodec.decode(restoredState.baselineJson))
        assertTrue(runCatching { SktjfBaselineCodec.encode(baseline.copy(termBounds = SktjfTermBounds(end, start))) }.isFailure)
        val priorDate = start.minusDays(1)
        val withPriorSourceDay = baseline.copy(calendar = baseline.calendar.copy(days = baseline.calendar.days +
            (priorDate to SktjfCalendarContent(SktjfDayType.A, null, null, null))))
        val restoredPrior = SktjfBaselineCodec.decode(SktjfBaselineCodec.encode(withPriorSourceDay))
        assertEquals(withPriorSourceDay, restoredPrior)
        assertEquals(SktjfWeekSelection.Selected(beforeId), resolveStoredSktjfOpportunity(restoredPrior, "shared", 1,
            start.plusWeeks(7), SktjfCycle.A, setOf("DD")))
        fun stored(date: LocalDate) = resolveStoredSktjfOpportunity(restored, "shared", 1, date,
            SktjfCycle.A, setOf("DD"))
        assertEquals(SktjfWeekSelection.Selected(beforeId), stored(start.plusWeeks(7)))
        assertEquals(SktjfWeekSelection.Selected("after"), stored(start.plusWeeks(8)))
        assertEquals(SktjfWeekSelection.Empty, stored(start.plusWeeks(8).plusDays(1)))
        val local = restored.lessons.lessons.map { (id, content) ->
            SktjfLocalLesson("local-$id", id, content.copy(subject = "編集済み $id"))
        }
        fun resolveLocal(items: List<SktjfLocalLesson>) = resolveLocalSktjfOpportunity(restored, items, "shared", 1,
            start.plusWeeks(8), SktjfCycle.A, setOf("DD"))
        assertEquals(SktjfWeekSelection.Selected(local.single { it.sourceLessonId == "after" }), resolveLocal(local))
        assertEquals(SktjfWeekSelection.Empty, resolveLocal(local.filterNot { it.sourceLessonId == "after" }))
        assertEquals(SktjfWeekSelection.Conflict, resolveLocal(local + local.first().copy(localKey = "personal", sourceLessonId = null)))
        assertEquals(SktjfWeekSelection.Conflict, resolveLocal(local + local.single { it.sourceLessonId == "after" }.copy(localKey = "duplicate")))
        assertEquals(SktjfWeekSelection.UnconfirmedWeek, resolveStoredSktjfOpportunity(
            restored.copy(termBounds = null), "shared", 1, start.plusWeeks(8), SktjfCycle.A, setOf("DD")))
        assertEquals(SktjfWeekSelection.UnconfirmedWeek, resolveStoredSktjfOpportunity(
            restored.copy(series = restored.series.map { it.copy(countingPolicy = null) }), "shared", 1,
            start.plusWeeks(8), SktjfCycle.A, setOf("DD"), start, end))
        assertEquals(SktjfWeekSelection.UnconfirmedWeek, resolveSktjfV2Opportunity(candidate, "shared", 1, start.plusWeeks(8),
            SktjfCycle.A, setOf("DD"), policy.copy(sources = listOf(SktjfReference("unknown", 1, null)))))
        assertEquals(SktjfWeekSelection.UnconfirmedWeek,
            resolveSktjfV2Opportunity(candidate, "unknown", 1, start, SktjfCycle.A, setOf("DD")) { _, _ -> SktjfOpportunityDecision(true, mapOf(0 to true)) })
        val changedCalendar = candidate.document.calendar.map { day ->
            if (day.date == start.plusWeeks(2)) day.copy(event = SktjfEvent("行事", SktjfEventKind.EVENT)) else day
        }
        val withEvent = candidate.copy(document = candidate.document.copy(calendar = changedCalendar))
        assertEquals("after", (resolveSktjfV2Opportunity(withEvent, "shared", 1, start.plusWeeks(8),
            SktjfCycle.A, setOf("DD"), policy) as SktjfWeekSelection.Selected).value.id)
        assertEquals(SktjfWeekSelection.UnconfirmedWeek, resolveSktjfV2Opportunity(withEvent, "shared", 1,
            start.plusWeeks(8), SktjfCycle.A, setOf("DD"), policy.copy(eventCounts = mapOf(SktjfEventKind.EVENT to null))))
    }
}
