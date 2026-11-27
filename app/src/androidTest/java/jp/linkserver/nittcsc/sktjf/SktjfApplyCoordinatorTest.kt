package jp.linkserver.nittcsc.sktjf

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.LessonWeekRange
import jp.linkserver.nittcsc.logic.ExportRange
import jp.linkserver.nittcsc.logic.CLASS_SLOTS
import jp.linkserver.nittcsc.widget.WidgetData
import jp.linkserver.nittcsc.widget.WidgetDataHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeNotNull
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class SktjfApplyCoordinatorTest {
    private val date = LocalDate.of(2026, 4, 6)

    @Test fun publishedBetaIsDiscoveredAndDownloadedByScheduler() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("sktjfLiveVerify") == "true")
        val client = SktjfDistributionClient(SKTJF_DISTRIBUTION_REPOSITORY, beta = true)
        for ((term, revision) in listOf("S" to "2026.S.2.Beta", "F" to "2026.F.1.Beta")) {
            assertEquals(SktjfDiscoveryResult.Found(revision), client.discover(2026, term))
            for (classId in listOf("1-1", "4M", "5M")) {
                val result = client.fetch(revision, classId)
                assertTrue("Cannot fetch $revision $classId: $result", result is SktjfFetchResult.Success)
                val success = result as SktjfFetchResult.Success
                assertEquals(classId, success.document.classId)
                assertEquals(revision, success.document.revision)
            }
        }
    }

    @Test fun reviewedReleaseAssetsAreAcceptedByTheScheduler() = runBlocking {
        val name = InstrumentationRegistry.getArguments().getString("sktjfCandidateBundle")
        assumeNotNull(name)
        require(name != null && Regex("^[a-zA-Z0-9._-]+$").matches(name))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bundle = JSONObject(File(context.filesDir, name).readText(Charsets.UTF_8))
        val expectedClasses = (1..4).map { "1-$it" }.toSet() +
            (2..5).flatMap { year -> listOf("M", "E", "I", "B").map { "$year$it" } }
        for (term in listOf("S", "F")) {
            val entry = bundle.getJSONObject(term)
            val manifest = SktjfV2DistributionJson.manifest(
                entry.getString("manifest").toByteArray(Charsets.UTF_8), "Link2011-Act2/NITTC_Timetable")
            assertEquals(term, manifest.common.term)
            assertEquals(expectedClasses, manifest.common.assets.map { it.classId }.toSet())
            val files = entry.getJSONObject("assets")
            for (asset in manifest.common.assets) {
                val assetBytes = files.getString(asset.name).toByteArray(Charsets.UTF_8)
                val parsed = SktjfV2DistributionJson.verifiedDocument(assetBytes, manifest, asset)
                val legacySelection = (parsed as? SktjfCandidateAsset.V2)?.candidate?.let {
                    SktjfLegacySelectionCatalog.adapt(assetBytes, it)
                }
                if (legacySelection != null) assertTrue(SktjfJson.localAsset(assetBytes.inputStream()) is SktjfCandidateAsset.V3)
                val document = when (parsed) {
                    is SktjfCandidateAsset.V1 -> parsed.document
                    is SktjfCandidateAsset.V2 -> parsed.candidate.document
                    is SktjfCandidateAsset.V3 -> error("This published v2 manifest cannot contain a v3 asset")
                }
                assertEquals(asset.classId, document.classId)
                assertEquals(term, document.term)
                assertEquals(manifest.common.revision, document.revision)
                for ((dd, international) in listOf(false to false, true to false, false to true, true to true)) {
                    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
                    try {
                        val dao = db.schedulerDao()
                        dao.upsertSettings(SettingsEntity(termStart = document.start, termEnd = document.end,
                            enableSemesterTimetables = true))
                        dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
                        dao.upsertNittcProfiles(listOf(NittcProfileEntity(document.academicYear, "UNDERGRADUATE",
                            document.classId.first().digitToInt(), document.classId, dd, international)))
                        val repo = SchedulerRepository(db, UiDesignPreferences(context))
                        val attributes = buildSet {
                            if (dd) add("DD")
                            if (international) add("INTERNATIONAL")
                        }
                        val timed = (parsed as? SktjfCandidateAsset.V2)?.candidate
                        val options = sktjfProfileSlotOptions(timed?.documentForMerge() ?: document, attributes)
                        val timedSlots = options.filter { slot -> timed?.series?.any { group ->
                            group.dayOfWeek == slot.day && slot.period in group.periods &&
                                slot.lessons.all { it.id in group.lessonIds } && slot.lessons.any { it.id in timed.ranges }
                        } == true }.map { it.day to it.period }.toSet()
                        val personal = options.filterNot { (it.day to it.period) in timedSlots }
                        val choice = personal.flatMap { slot ->
                            slot.lessons.firstOrNull { it.cycle == SktjfCycle.WEEKLY }?.let { listOf(it.id) }
                                ?: listOfNotNull(slot.lessons.firstOrNull { it.cycle == SktjfCycle.A }?.id,
                                    slot.lessons.firstOrNull { it.cycle == SktjfCycle.B }?.id)
                        }.toSet()
                        val personalDocument = document.copy(lessons = document.lessons.filterNot {
                            (it.dayOfWeek to it.period) in timedSlots
                        })
                        val selected = requireNotNull(selectSktjfLessons(personalDocument, attributes, choice)) {
                            "Cannot select courses for $term ${asset.classId} DD=$dd international=$international"
                        }
                        val preview = if (legacySelection != null) repo.previewSelectionSktjf(legacySelection)?.forReview() else when (parsed) {
                            is SktjfCandidateAsset.V1 ->
                                (repo.previewSktjf(selected) as? SktjfPreviewResult.Ready)?.preview
                            is SktjfCandidateAsset.V2 -> {
                                val lessons = selected.lessons + document.lessons.filter {
                                    (it.dayOfWeek to it.period) in timedSlots
                                }
                                val ids = lessons.map { it.id }.toSet()
                                val candidate = parsed.candidate.copy(document = document.copy(lessons = lessons),
                                    ranges = parsed.candidate.ranges.filterKeys { it in ids },
                                    series = parsed.candidate.series.mapNotNull { group ->
                                        val members = group.lessonIds.intersect(ids)
                                        if (members.isEmpty()) null else group.copy(lessonIds = members)
                                    })
                                repo.previewTimedSktjf(candidate)?.forReview()
                            }
                            is SktjfCandidateAsset.V3 -> error("This published v2 manifest cannot contain a v3 asset")
                        }
                        assertNotNull("Cannot preview $term ${asset.classId} DD=$dd international=$international", preview)
                        val ready = requireNotNull(preview)
                        val applied = repo.applySktjf(ready,
                            ready.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL },
                            ready.calendarConflicts.associate { it.date to SktjfConflictChoice.KEEP_LOCAL })
                        assertEquals("Cannot apply $term ${asset.classId} DD=$dd international=$international", SktjfApplyResult.Applied, applied)
                        val source = dao.getSktjfSourceStates().single()
                        assertEquals(document.revision, source.revision)
                        assertEquals("Source differs for $term ${asset.classId} DD=$dd international=$international",
                            ready.document.lessons.associate { it.id to it.content() },
                            SktjfBaselineCodec.decode(source.baselineJson).lessons.lessons)
                        assertTrue("No lessons for $term ${asset.classId} DD=$dd international=$international", dao.getLessonsOnce().isNotEmpty())
                    } finally { db.close() }
                }
            }
        }
    }

    @Test fun publishedFallBetaCanSwitchClassesInBothDirections() = runBlocking {
        val name = InstrumentationRegistry.getArguments().getString("sktjfCandidateBundle")
        assumeNotNull(name)
        require(name != null && Regex("^[a-zA-Z0-9._-]+$").matches(name))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bundle = JSONObject(File(context.filesDir, name).readText(Charsets.UTF_8)).getJSONObject("F")
        val manifest = SktjfV2DistributionJson.manifest(
            bundle.getString("manifest").toByteArray(Charsets.UTF_8), SKTJF_DISTRIBUTION_REPOSITORY)
        assertEquals("2026.F.1.Beta", manifest.common.revision)
        val assets = listOf("4M", "3I", "1-4").associateWith { classId ->
            val asset = manifest.common.assets.single { it.classId == classId }
            val bytes = bundle.getJSONObject("assets").getString(asset.name).toByteArray(Charsets.UTF_8)
            when (val parsed = SktjfV2DistributionJson.verifiedDocument(bytes, manifest, asset)) {
                is SktjfCandidateAsset.V1 -> SktjfFetchResult.Success(manifest.common, parsed.document)
                is SktjfCandidateAsset.V2 -> SktjfFetchResult.Success(manifest.common, parsed.candidate.document,
                    parsed.candidate, SktjfLegacySelectionCatalog.adapt(bytes, parsed.candidate))
                is SktjfCandidateAsset.V3 -> error("Version 3 is not valid in a version 2 manifest")
            }
        }
        assertEquals(assets.getValue("3I").document.calendar.map { it.date to it.content() },
            assets.getValue("1-4").document.calendar.map { it.date to it.content() })
        val initial = assets.getValue("4M").document
        val calendarConflicts = mutableListOf<List<Pair<LocalDate, SktjfCalendarContent>>>()
        for (destination in listOf("3I", "1-4")) {
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            val restoredDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            try {
                val dao = db.schedulerDao()
                dao.upsertSettings(SettingsEntity(termStart = initial.start, termEnd = initial.end,
                    enableSemesterTimetables = true))
                dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
                val repo = SchedulerRepository(db, UiDesignPreferences(context))
                fun profile(classId: String, dd: Boolean = false, international: Boolean = false) =
                    NittcProfileEntity(2026, "UNDERGRADUATE", classId.first().digitToInt(), classId, dd, international)
                suspend fun prepare(repository: SchedulerRepository, classId: String): SktjfApplyPreview {
                    val asset = assets.getValue(classId)
                    val preview = when {
                        asset.selections != null -> repository.previewSelectionSktjf(asset.selections)?.forReview()
                        asset.timed != null -> repository.previewTimedSktjf(asset.timed)?.forReview()
                        else -> (repository.previewSktjf(asset.document) as? SktjfPreviewResult.Ready)?.preview
                    }
                    return requireNotNull(preview) { "Cannot review $classId" }
                }
                suspend fun apply(repository: SchedulerRepository, preview: SktjfApplyPreview) {
                    val lessons = jp.linkserver.nittcsc.ui.initialSktjfLessonChoices(preview, false)
                    val dates = preview.calendarConflicts.associate { it.date to SktjfConflictChoice.KEEP_LOCAL }
                    assertEquals("Cannot apply ${preview.document.classId}", SktjfApplyResult.Applied,
                        repository.applySktjf(preview, lessons, dates))
                }
                dao.upsertNittcProfiles(listOf(profile("4M")))
                apply(repo, prepare(repo, "4M"))
                val rows = dao.getLessonsOnce()
                val extras = dao.getSktjfExtraLessons()
                val regular = rows.first { it.weeklySubject.isNotEmpty() && it.dayOfWeek == 1 && it.slotIndex == 0 }
                dao.upsertTask(TaskEntity(lessonId = regular.id, subject = regular.weeklySubject,
                    title = "Task from 4M", dueDate = initial.start, createdDate = initial.start))
                dao.upsertPlan(PlanEntity(lessonId = regular.id, subject = regular.weeklySubject,
                    title = "Plan from 4M", dueDate = initial.start, createdDate = initial.start))
                val personalDate = dao.getDayTypesOnce().first { it.dayType == DayType.A }.copy(dayType = DayType.B)
                dao.upsertDayTypes(listOf(personalDate))
                val days = dao.getDayTypesOnce()
                val beforeSwitch = requireNotNull(EffectiveSchoolSchedule.load(dao))
                dao.upsertNittcProfiles(listOf(profile(destination)))
                val preview = prepare(repo, destination)
                val pendingSwitch = requireNotNull(EffectiveSchoolSchedule.load(dao))
                days.forEach { assertEquals(beforeSwitch.lessonsOn(it.date), pendingSwitch.lessonsOn(it.date)) }
                assertEquals(rows, dao.getLessonsOnce())
                assertEquals(days, dao.getDayTypesOnce())
                assertEquals("4M", dao.getSktjfSourceStates().single().classId)
                assertEquals(extras, dao.getSktjfExtraLessons())
                calendarConflicts += preview.calendarConflicts.map { it.date to it.incoming }
                apply(repo, prepare(repo, destination))
                assertEquals(destination, dao.getSktjfSourceStates().single().classId)
                assertTrue(dao.getSktjfExtraLessons().all { it.classId == destination })
                assertTrue(dao.getSktjfSelectionGroups().all { it.classId == destination })
                assertEquals(personalDate, dao.getDayTypesOnce().single { it.date == personalDate.date })
                assertEquals("Task from 4M", dao.getTasksOnce().single().title)
                assertNull(dao.getTasksOnce().single().lessonId)
                assertEquals("Plan from 4M", dao.getPlansOnce().single().title)
                assertNull(dao.getPlansOnce().single().lessonId)
                apply(repo, prepare(repo, destination))
                if (destination == "1-4") {
                    val previousIds = SktjfBaselineCodec.decode(dao.getSktjfSourceStates().single().baselineJson).lessons.lessons.keys
                    dao.upsertNittcProfiles(listOf(profile(destination, dd = true, international = true)))
                    val attributesPreview = prepare(repo, destination)
                    assertNotEquals(previousIds, attributesPreview.document.lessons.map { it.id }.toSet())
                    apply(repo, attributesPreview)
                }

                val restored = SchedulerRepository(restoredDb, UiDesignPreferences(context))
                restored.importAllData(repo.exportAllData(), requireSettings = true)
                val restoredDao = restoredDb.schedulerDao()
                assertEquals(destination, restoredDao.getSktjfSourceStates().single().classId)
                restoredDao.upsertNittcProfiles(listOf(profile("4M", dd = true, international = true)))
                apply(restored, prepare(restored, "4M"))
                assertEquals("4M", restoredDao.getSktjfSourceStates().single().classId)
                assertTrue(restoredDao.getSktjfExtraLessons().all { it.classId == "4M" })
                restoredDao.upsertNittcProfiles(listOf(profile("4M")))
                apply(restored, prepare(restored, "4M"))
            } finally {
                restoredDb.close()
                db.close()
            }
        }
        assertEquals(calendarConflicts[0], calendarConflicts[1])
    }

    private fun document(): SktjfDocument {
        val source = SktjfReference("src", 1, null)
        return SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0",
            listOf(SktjfSource("src", "test.pdf", "a".repeat(64), null)),
            listOf(SktjfPeriod(1, LocalTime.of(8, 40), LocalTime.of(10, 10))),
            listOf(SktjfSlot(1, 1, SktjfSlotState.PRESENT, false, listOf(source))),
            listOf(SktjfLesson("lesson-1", 1, 1, SktjfCycle.WEEKLY, "配信授業", listOf("先生"), emptyList(),
                SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null, listOf(source))),
            listOf(SktjfCalendarDay(date, SktjfDayType.B, null, null, null, listOf(source))))
    }

    @Test fun switchingFromSelectionsKeepsManualEditsAndRetiresOldCandidates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date, enableSemesterTimetables = true))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            suspend fun setClass(classId: String) {
                dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, classId, false, false)))
            }
            val ordinary = document()
            val first = ordinary.lessons.single()
            val options = listOf(first.copy(id = "elective-a", period = 2, subject = "選択A"),
                first.copy(id = "elective-b", period = 2, subject = "選択B"))
            val group = SktjfSelectionGroup("elective", 1, 2, options.map { it.id }, first.sources)
            val elective = SktjfV3Candidate(SktjfV2Candidate(ordinary.copy(
                periods = ordinary.periods + SktjfPeriod(2, CLASS_SLOTS[1].start, CLASS_SLOTS[1].end),
                lessons = ordinary.lessons + options,
                slotCoverage = ordinary.slotCoverage + ordinary.slotCoverage.single().copy(period = 2)),
                emptyMap(), emptyMap()), listOf(group))
            setClass("1-1")
            val firstImport = requireNotNull(repository.previewSelectionSktjf(elective, mapOf(group.id to options.last().id))).forReview()
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(firstImport, emptyMap(), emptyMap()))
            val row = dao.getLessonsOnce().single()
            dao.upsertLesson(row.copy(weeklySubject = "手動編集"))
            val linkedExtra = dao.getSktjfExtraLessons().single { it.sourceLessonId == options.last().id }
            dao.upsertTask(TaskEntity(extraLessonId = linkedExtra.id, subject = options.last().subject,
                title = "選択授業の課題", dueDate = date, createdDate = date))

            setClass("1-2")
            assertTrue(requireNotNull(EffectiveSchoolSchedule.load(dao)).lessonsOn(date).any { it.content.extraLessonId == linkedExtra.id })
            val newClass = ordinary.copy(classId = "1-2", lessons = listOf(first.copy(subject = "変更先の授業")))
            val previousSource = dao.getSktjfSourceStates().single()
            dao.upsertSktjfSourceStates(listOf(previousSource.copy(requiresRelinking = true)))
            val unlinkedPreview = (repository.previewSktjf(newClass) as SktjfPreviewResult.Ready).preview
            assertFalse(unlinkedPreview.lessonReview.any { it.current?.subject == options.first().subject })
            dao.upsertSktjfSourceStates(listOf(previousSource))
            val preview = (repository.previewSktjf(newClass) as SktjfPreviewResult.Ready).preview
            assertEquals("手動編集", preview.lessonConflicts.single().overlappingLocal.single().content.subject)
            assertEquals(2, dao.getSktjfExtraLessons().size)
            val choices = jp.linkserver.nittcsc.ui.initialSktjfLessonChoices(preview, false)
            assertEquals(SktjfConflictChoice.KEEP_LOCAL, choices[first.id])
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(preview, choices, emptyMap()))
            assertEquals("手動編集", dao.getLessonsOnce().single().weeklySubject)
            assertTrue(dao.getSktjfSelectionGroups().isEmpty())
            assertTrue(dao.getSktjfExtraLessons().isEmpty())
            assertEquals("選択授業の課題", dao.getTasksOnce().single().title)
            assertNull(dao.getTasksOnce().single().extraLessonId)

            setClass("1-1")
            val returnPreview = requireNotNull(repository.previewSelectionSktjf(elective)).forReview()
            val returnChoices = jp.linkserver.nittcsc.ui.initialSktjfLessonChoices(returnPreview, false)
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(returnPreview, returnChoices, emptyMap()))
            assertNull(dao.getSktjfSelectionGroups().single().selectedLessonId)
            assertEquals(setOf("elective-a", "elective-b"), dao.getSktjfExtraLessons().mapNotNull { it.sourceLessonId }.toSet())
            assertEquals("手動編集", dao.getLessonsOnce().single().weeklySubject)
        } finally {
            db.close()
        }
    }

    @Test fun explicitSelectionGroupStoresAllCandidatesWithoutGuessing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao(); val initial = document()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date, enableSemesterTimetables = true))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026,"UNDERGRADUATE",1,"1-1",false,false)))
            val first = initial.lessons.single(); val second = first.copy(id = "lesson-2", subject = "別の選択授業")
            val doc = initial.copy(revision = "2026.S.0.Beta", lessons = listOf(first, second))
            val group = SktjfSelectionGroup("choice", 1, 1, listOf(first.id, second.id), first.sources)
            val candidate = SktjfV3Candidate(SktjfV2Candidate(doc, emptyMap(), emptyMap()), listOf(group))
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            val preview = requireNotNull(repository.previewSelectionSktjf(candidate)).forReview()
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(preview, emptyMap(), emptyMap()))
            assertEquals(setOf(first.id, second.id), dao.getSktjfExtraLessons().mapNotNull { it.sourceLessonId }.toSet())
            assertTrue(dao.getLessonsOnce().none { it.weeklySubject.isNotBlank() })
            val stored = dao.getSktjfSelectionGroups().single()
            assertEquals("choice", stored.groupId); assertNull(stored.selectedLessonId)
            assertTrue(requireNotNull(EffectiveSchoolSchedule.load(dao)).lessonsOn(date).isEmpty())
            assertTrue(requireNotNull(EffectiveSchoolSchedule.load(dao)).notificationLessonsOn(date).isEmpty())
            assertTrue(repository.generateLessons(ExportRange.Custom(date, date), date).isEmpty())
            suspend fun widget(): WidgetData {
                val dayRows = dao.getDayTypesOnce().associateBy { it.date }
                val elective = SktjfElectiveSchedule.create(SchedulerUsageMode.NITTC, dao.getNittcProfilesOnce(),
                    dao.getSktjfSelectionGroups(), dao.getSktjfExtraLessons(), dao.getSktjfSourceStates())
                return WidgetData(date, dao.getSettings(), CLASS_SLOTS, DayType.B, dayRows,
                    dayRows.mapValues { it.value.dayType }, emptySet(), emptyMap(), dao.getLessonsOnce().associateBy { it.lessonKey() },
                    emptyMap(), emptyMap(), emptyList(), emptyList(), electiveSchedule = elective)
            }
            assertNull(WidgetDataHelper.resolveLesson(widget(), date, 0))
            assertTrue(repository.selectSktjfCourse(stored, second.id))
            assertEquals(second.id, dao.getSktjfSelectionGroups().single().selectedLessonId)
            assertEquals(listOf(second.subject), requireNotNull(EffectiveSchoolSchedule.load(dao)).lessonsOn(date).map { it.content.subject })
            assertEquals(listOf(second.subject), requireNotNull(EffectiveSchoolSchedule.load(dao)).notificationLessonsOn(date).map { it.content.subject })
            assertEquals(listOf(second.subject), repository.generateLessons(ExportRange.Custom(date, date), date).map { it.subject })
            assertEquals(second.subject, WidgetDataHelper.resolveLesson(widget(), date, 0)?.subject)
            val officialGroup = group.copy(id = "document-choice")
            val renamed = candidate.copy(timetable = candidate.timetable.copy(document = doc.copy(revision = "2026.S.1.Beta")),
                selectionGroups = listOf(officialGroup))
            val renamedPreview = requireNotNull(repository.previewSelectionSktjf(renamed)).forReview()
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(renamedPreview,
                renamedPreview.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL }, emptyMap()))
            assertEquals("document-choice", dao.getSktjfSelectionGroups().single().groupId)
            assertEquals(second.id, dao.getSktjfSelectionGroups().single().selectedLessonId)
            val removedLocalId = dao.getSktjfExtraLessons().single { it.sourceLessonId == second.id }.id
            val third = first.copy(id = "lesson-3", subject = "新しい選択授業")
            val updatedDoc = doc.copy(revision = "2026.S.2.Beta", lessons = listOf(first, third))
            val updatedGroup = officialGroup.copy(lessonIds = listOf(first.id, third.id))
            val updated = SktjfV3Candidate(SktjfV2Candidate(updatedDoc, emptyMap(), emptyMap()), listOf(updatedGroup))
            val task = TaskEntity(id = 95, extraLessonId = removedLocalId, subject = second.subject,
                title = "課題", dueDate = date, createdDate = date)
            dao.upsertTask(task)
            val protectedPreview = requireNotNull(repository.previewSelectionSktjf(updated)).forReview()
            val protectedChoices = protectedPreview.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL }
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(protectedPreview, protectedChoices, emptyMap()))
            assertEquals(task, dao.getTasksOnce().single())
            assertEquals(removedLocalId, dao.getSktjfExtraLessons().single { it.sourceLessonId == second.id }.id)
            assertEquals(setOf(second.id), sktjfGroupIds(dao.getSktjfSelectionGroups().single().retiredLessonIdsJson))
            assertTrue(requireNotNull(EffectiveSchoolSchedule.load(dao)).lessonsOn(date).isEmpty())
            assertTrue(requireNotNull(EffectiveSchoolSchedule.load(dao)).notificationLessonsOn(date).isEmpty())
            assertTrue(repository.generateLessons(ExportRange.Custom(date, date), date).isEmpty())
            val updatePreview = requireNotNull(repository.previewSelectionSktjf(updated)).forReview()
            val updateChoices = updatePreview.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL }
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(updatePreview, updateChoices, emptyMap()))
            assertEquals(second.id, dao.getSktjfSelectionGroups().single().selectedLessonId)
            assertEquals(setOf(first.id, second.id, third.id), dao.getSktjfExtraLessons().mapNotNull { it.sourceLessonId }.toSet())
            assertNotEquals(removedLocalId, dao.getSktjfExtraLessons().single { it.sourceLessonId == third.id }.id)
            assertTrue(requireNotNull(EffectiveSchoolSchedule.load(dao)).lessonsOn(date).isEmpty())
            assertEquals(task, dao.getTasksOnce().single())
            val restored = candidate.copy(timetable = candidate.timetable.copy(document = doc.copy(revision = "2026.S.3.Beta")),
                selectionGroups = listOf(officialGroup))
            val restoredPreview = requireNotNull(repository.previewSelectionSktjf(restored)).forReview()
            val restoreChoices = restoredPreview.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.APPLY_INCOMING }
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(restoredPreview, restoreChoices, emptyMap()))
            assertEquals(removedLocalId, dao.getSktjfExtraLessons().single { it.sourceLessonId == second.id }.id)
            assertEquals(task, dao.getTasksOnce().single())
            assertTrue(sktjfGroupIds(dao.getSktjfSelectionGroups().single().retiredLessonIdsJson).isEmpty())
            assertEquals(listOf(second.subject), requireNotNull(EffectiveSchoolSchedule.load(dao)).lessonsOn(date).map { it.content.subject })
            val emptyDoc = doc.copy(revision = "2026.S.4.Beta", lessons = emptyList(),
                slotCoverage = doc.slotCoverage.map { it.copy(state = SktjfSlotState.EMPTY) })
            val removedGroup = SktjfV3Candidate(SktjfV2Candidate(emptyDoc, emptyMap(), emptyMap()), emptyList())
            val emptyPreview = requireNotNull(repository.previewSelectionSktjf(removedGroup)).forReview()
            val emptyChoices = emptyPreview.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.APPLY_INCOMING }
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(emptyPreview, emptyChoices, emptyMap()))
            assertEquals(task, dao.getTasksOnce().single())
            assertEquals(listOf(removedLocalId), dao.getSktjfExtraLessons().map { it.id })
            val removedState = dao.getSktjfSelectionGroups().single()
            assertEquals(second.id, removedState.selectedLessonId)
            assertEquals(setOf(second.id), sktjfGroupIds(removedState.retiredLessonIdsJson))
            assertTrue(requireNotNull(EffectiveSchoolSchedule.load(dao)).notificationLessonsOn(date).isEmpty())
            dao.upsertLesson(LessonEntity(academicYear = 2026, timetableTerm = TimetableTerm.FIRST,
                dayOfWeek = 1, slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "手入力",
                weeklyTeacher = "先生", aSubject = "", aTeacher = "", bSubject = "", bTeacher = ""))
            assertEquals(listOf("手入力"), requireNotNull(EffectiveSchoolSchedule.load(dao)).lessonsOn(date).map { it.content.subject })
            assertEquals(listOf("手入力"), requireNotNull(EffectiveSchoolSchedule.load(dao)).notificationLessonsOn(date).map { it.content.subject })
            assertEquals(listOf("手入力"), repository.generateLessons(ExportRange.Custom(date, date), date).map { it.subject })
            assertEquals("手入力", WidgetDataHelper.resolveLesson(widget(), date, 0)?.subject)
            assertEquals(task, dao.getTasksOnce().single())
        } finally { db.close() }
    }

    @Test fun keepingAnExistingSlotDoesNotInstallIncomingSelectionGroup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao(); val initial = document()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date, enableSemesterTimetables = true))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            dao.upsertLesson(LessonEntity(id = 71, academicYear = 2026, timetableTerm = TimetableTerm.FIRST,
                dayOfWeek = 1, slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "手入力", weeklyTeacher = "先生",
                aSubject = "", aTeacher = "", bSubject = "", bTeacher = ""))
            val first = initial.lessons.single(); val second = first.copy(id = "lesson-2", subject = "別の選択授業")
            val doc = initial.copy(revision = "2026.S.0.Beta", lessons = listOf(first, second))
            val group = SktjfSelectionGroup("choice", 1, 1, listOf(first.id, second.id), first.sources)
            val candidate = SktjfV3Candidate(SktjfV2Candidate(doc, emptyMap(), emptyMap()), listOf(group))
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            val preview = requireNotNull(repository.previewSelectionSktjf(candidate)).forReview()
            assertTrue(preview.lessonConflicts.isNotEmpty())
            val choices = preview.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL }
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(preview, choices, emptyMap()))
            assertEquals("手入力", dao.getLessonsOnce().single().weeklySubject)
            assertTrue(dao.getSktjfSelectionGroups().isEmpty())
        } finally { db.close() }
    }

    @Test fun replacingExistingSlotWithSelectionKeepsTaskAndPlanReferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao(); val initial = document()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date, enableSemesterTimetables = true))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            dao.upsertLesson(LessonEntity(id = 71, academicYear = 2026, timetableTerm = TimetableTerm.FIRST,
                dayOfWeek = 1, slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "手入力", weeklyTeacher = "先生",
                aSubject = "", aTeacher = "", bSubject = "", bTeacher = ""))
            val task = TaskEntity(id = 93, lessonId = 71, subject = "手入力", title = "課題", dueDate = date, createdDate = date)
            val plan = PlanEntity(id = 94, lessonId = 71, subject = "手入力", title = "予定", dueDate = date, createdDate = date)
            dao.upsertTask(task); dao.upsertPlan(plan)
            val first = initial.lessons.single(); val second = first.copy(id = "lesson-2", subject = "別の選択授業")
            val doc = initial.copy(revision = "2026.S.0.Beta", lessons = listOf(first, second))
            val group = SktjfSelectionGroup("choice", 1, 1, listOf(first.id, second.id), first.sources)
            val candidate = SktjfV3Candidate(SktjfV2Candidate(doc, emptyMap(), emptyMap()), listOf(group))
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            val preview = requireNotNull(repository.previewSelectionSktjf(candidate)).forReview()
            val replace = preview.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.APPLY_INCOMING }
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(preview, replace, emptyMap()))
            assertEquals(setOf(first.id, second.id), dao.getSktjfExtraLessons().mapNotNull { it.sourceLessonId }.toSet())
            assertEquals(71L, dao.getLessonsOnce().single().id)
            assertEquals("", dao.getLessonsOnce().single().weeklySubject)
            assertEquals(task, dao.getTasksOnce().single())
            assertEquals(plan, dao.getPlansOnce().single())
            assertEquals("choice", dao.getSktjfSelectionGroups().single().groupId)
            assertTrue(repository.selectSktjfCourse(dao.getSktjfSelectionGroups().single(), first.id))
            val again = requireNotNull(repository.previewSelectionSktjf(candidate)).forReview()
            val choices = again.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL }
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(again, choices, emptyMap()))
            assertEquals(setOf(first.id, second.id), dao.getSktjfExtraLessons().mapNotNull { it.sourceLessonId }.toSet())
            assertEquals(first.id, dao.getSktjfSelectionGroups().single().selectedLessonId)
            assertEquals(task, dao.getTasksOnce().single())
            assertEquals(plan, dao.getPlansOnce().single())
        } finally { db.close() }
    }

    @Test fun addingAWeekSelectionPreservesTheExistingBWeek() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao(); val initial = document()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date, enableSemesterTimetables = true, enableAbTimetable = true))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            dao.upsertLesson(LessonEntity(id = 71, academicYear = 2026, timetableTerm = TimetableTerm.FIRST,
                dayOfWeek = 1, slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "手入力", weeklyTeacher = "先生",
                aSubject = "", aTeacher = "", bSubject = "", bTeacher = ""))
            val first = initial.lessons.single().copy(cycle = SktjfCycle.A)
            val second = first.copy(id = "lesson-2", subject = "別の選択授業")
            val doc = initial.copy(revision = "2026.S.0.Beta", lessons = listOf(first, second))
            val group = SktjfSelectionGroup("choice", 1, 1, listOf(first.id, second.id), first.sources)
            val candidate = SktjfV3Candidate(SktjfV2Candidate(doc, emptyMap(), emptyMap()), listOf(group))
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            val preview = requireNotNull(repository.previewSelectionSktjf(candidate)).forReview()
            val choices = preview.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.APPLY_INCOMING }
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(preview, choices, emptyMap()))
            val row = dao.getLessonsOnce().single()
            assertEquals(71L, row.id); assertEquals(LessonMode.ALTERNATING, row.mode)
            assertEquals("手入力", row.bSubject); assertEquals("", row.aSubject)
            assertEquals(setOf(first.id, second.id), dao.getSktjfExtraLessons().mapNotNull { it.sourceLessonId }.toSet())
            assertEquals(listOf("手入力"), requireNotNull(EffectiveSchoolSchedule.load(dao)).lessonsOn(date).map { it.content.subject })
        } finally { db.close() }
    }

    @Test fun ordinaryDistributedSlotCanBecomeASelectionWithoutLosingTask() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao(); val initial = document().copy(revision = "2026.S.0.Beta")
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date, enableSemesterTimetables = true))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            val original = (repository.previewSktjf(initial) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(original, emptyMap(), emptyMap()))
            val id = dao.getLessonsOnce().single().id
            val task = TaskEntity(id = 97, lessonId = id, subject = initial.lessons.single().subject,
                title = "課題", dueDate = date, createdDate = date)
            dao.upsertTask(task)
            val first = initial.lessons.single().copy(id = "new-a", subject = "候補A")
            val second = first.copy(id = "new-b", subject = "候補B")
            val updated = initial.copy(revision = "2026.S.1.Beta", lessons = listOf(first, second))
            val group = SktjfSelectionGroup("choice", 1, 1, listOf(first.id, second.id), first.sources)
            val candidate = SktjfV3Candidate(SktjfV2Candidate(updated, emptyMap(), emptyMap()), listOf(group))
            val preview = requireNotNull(repository.previewSelectionSktjf(candidate)).forReview()
            val choices = preview.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.APPLY_INCOMING }
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(preview, choices, emptyMap()))
            assertEquals(id, dao.getLessonsOnce().single().id)
            assertEquals(task, dao.getTasksOnce().single())
            assertEquals(setOf(first.id, second.id), dao.getSktjfExtraLessons().mapNotNull { it.sourceLessonId }.toSet())
        } finally { db.close() }
    }

    @Test fun reviewedLocalCandidateAppliesAndEnforcesTheTenthOpportunity() = runBlocking {
        val name = InstrumentationRegistry.getArguments().getString("sktjfCandidateFile")
        assumeNotNull(name)
        require(name != null && Regex("^[a-zA-Z0-9._-]+$").matches(name))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val candidate = SktjfV2CandidateJson.parse(File(context.filesDir, name).readBytes())
        assertEquals("4M", candidate.document.classId)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao(); val doc = candidate.document
            dao.upsertSettings(SettingsEntity(termStart = doc.start, termEnd = doc.end, enableSemesterTimetables = true))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 4, "4M", false, false)))
            val repo = SchedulerRepository(db, UiDesignPreferences(context))
            val preview = requireNotNull(repo.previewTimedSktjf(candidate)).forReview()
            val choices = preview.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL }
            val dates = preview.calendarConflicts.associate { it.date to SktjfConflictChoice.KEEP_LOCAL }
            assertEquals(SktjfApplyResult.Applied, repo.applySktjf(preview, choices, dates))
            val schedule = requireNotNull(EffectiveSchoolSchedule.load(dao))
            val tenth = schedule.lessonsOn(LocalDate.of(2026, 12, 22)).filter { it.slotIndex in 2..3 }
            assertEquals(listOf("工学実験・実習III", "工学実験・実習III"), tenth.map { it.content.subject })
            val eleventh = schedule.lessonsOn(LocalDate.of(2027, 1, 12)).filter { it.slotIndex in 2..3 }
            assertTrue(eleventh.none { it.content.subject == "工学実験・実習III" })
        } finally { db.close() }
    }

    @Test fun reviewedFifthYearCandidateShowsExamAndSwitchesAfterEighthOpportunity() = runBlocking {
        val name = InstrumentationRegistry.getArguments().getString("sktjfFifthYearFile")
        assumeNotNull(name)
        require(name != null && Regex("^[a-zA-Z0-9._-]+$").matches(name))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val candidate = SktjfV2CandidateJson.parse(File(context.filesDir, name).readBytes())
        assertEquals("5M", candidate.document.classId)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao(); val doc = candidate.document
            dao.upsertSettings(SettingsEntity(termStart = doc.start, termEnd = doc.end, enableSemesterTimetables = true))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 5, "5M", false, false)))
            val repo = SchedulerRepository(db, UiDesignPreferences(context))
            val preview = requireNotNull(repo.previewTimedSktjf(candidate)).forReview()
            assertEquals(SktjfApplyResult.Applied, repo.applySktjf(preview,
                preview.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL },
                preview.calendarConflicts.associate { it.date to SktjfConflictChoice.KEEP_LOCAL }))
            val examDate = LocalDate.of(2026, 11, 25)
            val exam = dao.getDayTypesOnce().single { it.date == examDate }
            assertEquals(DayType.HOLIDAY, exam.dayType)
            assertEquals(HolidaySpecialLabel.MIDTERM, exam.holidaySpecialLabel)
            assertEquals(SktjfDayType.B, SktjfBaselineCodec.decode(dao.getSktjfSourceStates().single().baselineJson).calendar.days[examDate]?.dayType)
            val schedule = requireNotNull(EffectiveSchoolSchedule.load(dao))
            assertTrue(schedule.lessonsOn(examDate).isEmpty())
            val before = schedule.lessonsOn(LocalDate.of(2026, 12, 2)).filter { it.slotIndex in 0..1 }
            val after = schedule.lessonsOn(LocalDate.of(2026, 12, 9)).filter { it.slotIndex in 0..1 }
            assertEquals(listOf("機械設計製図II", "機械設計製図II"), before.map { it.content.subject })
            assertEquals(listOf("卒研", "卒研"), after.map { it.content.subject })
            assertTrue(schedule.notificationLessonsOn(examDate).isEmpty())
            assertEquals(listOf("卒研", "卒研"), schedule.notificationLessonsOn(LocalDate.of(2026, 12, 9))
                .filter { it.slotIndex in 0..1 }.map { it.content.subject })
            val calendarLessons = repo.generateLessons(ExportRange.Custom(examDate, LocalDate.of(2026, 12, 9)))
            assertTrue(calendarLessons.none { it.date == examDate })
            assertEquals(listOf("卒研", "卒研"), calendarLessons.filter { it.date == LocalDate.of(2026, 12, 9) && it.slot.index in 0..1 }
                .map { it.subject })
            val dayRows = dao.getEffectiveDayTypes().associateBy { it.date }
            val rows = dao.getLessonsOnce(); val extras = dao.getSktjfExtraLessons()
            val timed = SktjfTimedSchedule.create(SchedulerUsageMode.NITTC, dao.getNittcProfilesOnce(),
                dao.getSktjfSourceStates(), rows, extras)
            val widget = WidgetData(LocalDate.of(2026, 12, 9), dao.getSettings(), CLASS_SLOTS, DayType.A,
                dayRows, dayRows.mapValues { it.value.dayType }, emptySet(), emptyMap(), rows.associateBy { it.lessonKey() },
                emptyMap(), emptyMap(), emptyList(), emptyList(), timedSchedule = timed)
            assertEquals("卒研", WidgetDataHelper.resolveLesson(widget, LocalDate.of(2026, 12, 9), 0)?.subject)
            assertNull(WidgetDataHelper.resolveLesson(widget, examDate, 0))
        } finally { db.close() }
    }

    @Test fun singleTimedLessonEndsAfterTenthOfficialOpportunityEvenWithPersonalCancellation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao(); val end = date.plusDays(76)
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = end, enableSemesterTimetables = true))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val ref = SktjfReference("src", 1, null)
            val original = document()
            val lesson = original.lessons.single().copy(id = "experiment", subject = "実習")
            val days = (0L..76L).map { date.plusDays(it) }.filter { it.dayOfWeek.value < 6 }
                .map { SktjfCalendarDay(it, SktjfDayType.A, null, null, null, listOf(ref)) }
            val doc = original.copy(end = end, revision = "2026.S.0.Beta", lessons = listOf(lesson), calendar = days)
            val policy = SktjfCountingPolicy(true, false, emptyMap(), true, sources = listOf(ref))
            val group = SktjfV2WeekSeries("experiment-series", 1, setOf(1), setOf("experiment"), SktjfCycle.WEEKLY, listOf(ref), policy)
            val candidate = SktjfV2Candidate(doc, mapOf("experiment" to SktjfV2Range(LessonWeekRange(end = 10), "～10週")), emptyMap(), listOf(group))
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            val preview = requireNotNull(repository.previewTimedSktjf(candidate)).forReview()
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(preview, emptyMap(), emptyMap()))
            assertTrue(dao.getSktjfExtraLessons().isEmpty())
            dao.upsertCancelledLesson(CancelledLessonEntity(date, 0))
            val schedule = requireNotNull(EffectiveSchoolSchedule.load(dao))
            assertTrue(schedule.lessonsOn(date).isEmpty())
            assertEquals("実習", schedule.lessonsOn(date.plusWeeks(9)).single().content.subject)
            assertTrue(schedule.lessonsOn(date.plusWeeks(10)).isEmpty())
            val sender = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            try {
                val senderDao = sender.schedulerDao()
                senderDao.upsertSettings(SettingsEntity(termStart = date, termEnd = end, enableSemesterTimetables = true))
                senderDao.upsertLesson(LessonEntity(academicYear = 2026, timetableTerm = TimetableTerm.FIRST,
                    dayOfWeek = 1, slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "共有授業",
                    weeklyTeacher = "先生", aSubject = "", aTeacher = "", bSubject = "", bTeacher = ""))
                val senderTransfer = QrShareTransfer(SchedulerRepository(sender, UiDesignPreferences(context)), sender)
                val incoming = senderTransfer.export(QrShareSelection(2026, setOf(QrShareSection.FIRST)))
                QrShareTransfer(repository, db).import(incoming, emptySet())
                assertEquals("共有授業", requireNotNull(EffectiveSchoolSchedule.load(dao)).lessonsOn(date.plusWeeks(9)).single().content.subject)
                assertEquals("共有授業", requireNotNull(EffectiveSchoolSchedule.load(dao)).lessonsOn(date.plusWeeks(10)).single().content.subject)
                assertEquals(setOf(1 to 1), decodeReplacedTimedSlots(dao.getSktjfSourceStates().single().replacedTimedSlotsJson))
                senderDao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
                senderDao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
                senderDao.upsertSktjfSourceStates(listOf(dao.getSktjfSourceStates().single().copy(replacedTimedSlotsJson = "[]")))
                val otherRepository = SchedulerRepository(sender, UiDesignPreferences(context))
                otherRepository.applySyncPayload(repository.exportSyncPayload())
                assertEquals(setOf(1 to 1), decodeReplacedTimedSlots(senderDao.getSktjfSourceStates().single().replacedTimedSlotsJson))
                assertEquals("共有授業", requireNotNull(EffectiveSchoolSchedule.load(senderDao)).lessonsOn(date.plusWeeks(10)).single().content.subject)
                for (localSubject in listOf("共有授業", "実習")) {
                  val fresh = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
                  try {
                    val freshDao = fresh.schedulerDao()
                    freshDao.upsertSettings(SettingsEntity(termStart = date, termEnd = end, enableSemesterTimetables = true))
                    freshDao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
                    freshDao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
                    val freshRepo = SchedulerRepository(fresh, UiDesignPreferences(context))
                    freshRepo.applySyncPayload(repository.exportSyncPayload())
                    assertTrue(freshDao.getSktjfSourceStates().isEmpty())
                    val syncedRow = freshDao.getLessonsOnce().single { it.dayOfWeek == 1 && it.slotIndex == 0 }
                    freshDao.upsertLesson(syncedRow.copy(weeklySubject = localSubject))
                    val incomingPreview = requireNotNull(freshRepo.previewTimedSktjf(candidate)).forReview()
                    assertTrue(incomingPreview.lessonConflicts.isNotEmpty())
                    assertEquals(SktjfApplyResult.Applied, freshRepo.applySktjf(incomingPreview,
                        incomingPreview.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL }, emptyMap()))
                    assertEquals(localSubject, requireNotNull(EffectiveSchoolSchedule.load(freshDao)).lessonsOn(date.plusWeeks(10)).single().content.subject)
                  } finally { fresh.close() }
                }
                val backup = repository.exportAllData()
                repository.importAllData(backup, requireSettings = true)
                assertEquals("共有授業", requireNotNull(EffectiveSchoolSchedule.load(dao)).lessonsOn(date.plusWeeks(10)).single().content.subject)
                val keep = requireNotNull(repository.previewTimedSktjf(candidate)).forReview()
                assertTrue(keep.lessonConflicts.isNotEmpty())
                assertEquals(SktjfApplyResult.Applied, repository.applySktjf(keep,
                    keep.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL }, emptyMap()))
                assertEquals("共有授業", requireNotNull(EffectiveSchoolSchedule.load(dao)).lessonsOn(date.plusWeeks(10)).single().content.subject)
                val restore = requireNotNull(repository.previewTimedSktjf(candidate)).forReview()
                assertEquals(SktjfApplyResult.Applied, repository.applySktjf(restore,
                    restore.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.APPLY_INCOMING }, emptyMap()))
                assertTrue(requireNotNull(EffectiveSchoolSchedule.load(dao)).lessonsOn(date.plusWeeks(10)).isEmpty())
            } finally { sender.close() }
        } finally { db.close() }
    }

    @Test fun timedInitialApplyWritesOneAtomicSourceAndKeepsAlternatives() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao(); val end = date.plusDays(62)
            val settings = SettingsEntity(termStart = date, termEnd = end, enableSemesterTimetables = true)
            dao.upsertSettings(settings)
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val original = document(); val before = original.lessons.single().copy(id = "before", subject = "製図")
            val after = before.copy(id = "after", subject = "卒研")
            val sourceRef = SktjfReference("src", 1, null)
            val calendar = (0L..62L).map { offset -> SktjfCalendarDay(date.plusDays(offset), SktjfDayType.A, null, null, null, listOf(sourceRef)) }
            val doc = original.copy(end = end, revision = "2026.S.0.Beta", lessons = listOf(before, after), calendar = calendar)
            val policy = SktjfCountingPolicy(true, false, emptyMap(), true, sources = listOf(sourceRef))
            val group = SktjfV2WeekSeries("shared", 1, setOf(1), setOf("before", "after"), SktjfCycle.WEEKLY, listOf(sourceRef), policy)
            val candidate = SktjfV2Candidate(doc, mapOf("before" to SktjfV2Range(LessonWeekRange(end = 8), "8週まで"),
                "after" to SktjfV2Range(LessonWeekRange(start = 9), "9週から")), emptyMap(), listOf(group))
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            val coordinator = SktjfApplyCoordinator(db, repository)
            val stale = requireNotNull(coordinator.previewV2(candidate))
            assertTrue(dao.getLessonsOnce().isEmpty()); assertTrue(dao.getSktjfSourceStates().isEmpty())
            dao.upsertSettings(settings.copy(termEnd = end.plusDays(1)))
            assertEquals(SktjfApplyResult.Stale, coordinator.applyV2(stale, emptyMap(), emptyMap()))
            assertTrue(dao.getLessonsOnce().isEmpty()); assertTrue(dao.getSktjfExtraLessons().isEmpty())
            dao.upsertSettings(settings)
            val preview = requireNotNull(repository.previewTimedSktjf(candidate)).forReview()
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(preview, emptyMap(), emptyMap()))
            assertEquals(1, dao.getLessonsOnce().size); assertEquals(1, dao.getSktjfExtraLessons().size)
            val saved = SktjfBaselineCodec.decode(dao.getSktjfSourceStates().single().baselineJson)
            assertEquals(setOf("before", "after"), saved.series.single().lessonIds)
            val schedule = requireNotNull(EffectiveSchoolSchedule.load(dao))
            assertEquals("製図", schedule.lessonsOn(date.plusWeeks(7)).single().content.subject)
            assertEquals("卒研", schedule.lessonsOn(date.plusWeeks(8)).single().content.subject)
            val row = dao.getLessonsOnce().single(); val alternativeId = dao.getSktjfExtraLessons().single().id
            dao.upsertTask(TaskEntity(id = 93, lessonId = row.id, subject = "製図", title = "課題", dueDate = date, createdDate = date))
            val again = requireNotNull(repository.previewTimedSktjf(candidate)).forReview()
            assertTrue(again.lessonConflicts.isEmpty())
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(again, emptyMap(), emptyMap()))
            assertEquals(row.id, dao.getLessonsOnce().single().id)
            assertEquals(alternativeId, dao.getSktjfExtraLessons().single().id)
            val local = requireNotNull(repository.previewTimedSktjf(candidate)).copy(localFile = true).forReview()
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(local, emptyMap(), emptyMap()))
            assertTrue(dao.getSktjfSourceStates().single().isLocalSktjfSource())
            dao.upsertLesson(row.copy(weeklySubject = "個人の製図"))
            val edited = requireNotNull(coordinator.previewV2(candidate))
            assertEquals(SktjfConflictReason.LOCAL_CHANGED, edited.preparation.lessons.conflicts.single().reason)
            assertEquals(SktjfApplyResult.Applied, coordinator.applyV2(edited, mapOf("before" to SktjfConflictChoice.KEEP_LOCAL), emptyMap()))
            assertEquals("個人の製図", dao.getLessonsOnce().single().weeklySubject)
            assertEquals(row.id, dao.getTaskById(93)!!.lessonId)
            assertEquals(alternativeId, dao.getSktjfExtraLessons().single().id)
            val revised = candidate.copy(document = doc.copy(revision = "2026.S.1.Beta", lessons = listOf(before.copy(subject = "改訂製図"), after)))
            val conflict = requireNotNull(coordinator.previewV2(revised))
            assertEquals(SktjfConflictReason.BOTH_CHANGED, conflict.preparation.lessons.conflicts.single().reason)
            assertEquals(SktjfApplyResult.Applied, coordinator.applyV2(conflict, mapOf("before" to SktjfConflictChoice.KEEP_LOCAL), emptyMap()))
            assertEquals("個人の製図", dao.getLessonsOnce().single().weeklySubject)
            assertEquals(row.id, dao.getTaskById(93)!!.lessonId)
            assertEquals(alternativeId, dao.getSktjfExtraLessons().single().id)
            assertEquals("改訂製図", SktjfBaselineCodec.decode(dao.getSktjfSourceStates().single().baselineJson).lessons.lessons.getValue("before").subject)
            val replace = requireNotNull(repository.previewTimedSktjf(revised)).forReview()
            assertEquals(SktjfApplyResult.Applied, repository.applySktjf(replace,
                mapOf("before" to SktjfConflictChoice.APPLY_INCOMING), emptyMap()))
            assertEquals("改訂製図", dao.getLessonsOnce().single().weeklySubject)
            assertEquals(row.id, dao.getTaskById(93)!!.lessonId)
            assertEquals("製図", dao.getTaskById(93)!!.subject)
            val backup = JSONObject(repository.exportAllData())
            assertEquals(30, backup.getInt("version"))
            repository.importAllData(backup.toString(), requireSettings = true)
            val restored = SktjfBaselineCodec.decode(dao.getSktjfSourceStates().single().baselineJson)
            assertEquals(9, restored.lessons.lessons.getValue("after").weekRange?.range?.start)
            assertEquals(alternativeId, dao.getSktjfExtraLessons().single().id)
            assertEquals(dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.id, dao.getTasksOnce().single().lessonId)
            fun editable() = SktjfTimedSchedule.create(SchedulerUsageMode.NITTC,
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)),
                runBlocking { dao.getSktjfSourceStates() }, runBlocking { dao.getLessonsOnce() },
                runBlocking { dao.getSktjfExtraLessons() }).alternativesFor(2026, TimetableTerm.FIRST, 1, 1).single()
            dao.upsertTask(TaskEntity(id = 94, extraLessonId = alternativeId, subject = "卒研", title = "卒研課題", dueDate = date, createdDate = date))
            dao.upsertTask(TaskEntity(id = 95, subject = "卒研", title = "独立課題", dueDate = date, createdDate = date))
            dao.upsertPlan(PlanEntity(id = 96, extraLessonId = alternativeId, subject = "卒研", title = "卒研予定", dueDate = date, createdDate = date))
            assertTrue(repository.editSktjfExtraLesson(editable(), SktjfExtraLessonDraft("卒研（発展）", "", "")))
            assertEquals("卒研（発展）", dao.getTaskById(94)!!.subject)
            assertEquals("卒研（発展）", dao.getPlansOnce().single().subject)
            assertEquals("卒研", dao.getTaskById(95)!!.subject)
            assertTrue(repository.editSktjfExtraLesson(editable(), SktjfExtraLessonDraft("別科目", "", "")))
            assertEquals("卒研（発展）", dao.getTaskById(94)!!.subject)
            assertEquals("卒研（発展）", dao.getPlansOnce().single().subject)
            assertTrue(repository.editSktjfExtraLesson(editable(), SktjfExtraLessonDraft("卒研（発展）", "", "")))
            assertTrue(repository.editSktjfExtraLesson(editable(), SktjfExtraLessonDraft("別科目", "", "", true)))
            assertEquals("別科目", dao.getTaskById(94)!!.subject)
            assertEquals("別科目", dao.getPlansOnce().single().subject)
            assertEquals("卒研", dao.getTaskById(95)!!.subject)
        } finally { db.close() }
    }

    @Test fun stalePreviewDoesNotWriteAndConfirmedApplyPreservesLessonId() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao(); val repository = SchedulerRepository(db, UiDesignPreferences(context))
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val row = LessonEntity(id = 71, academicYear = 2026, timetableTerm = TimetableTerm.FIRST, dayOfWeek = 1,
                slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "手入力", weeklyTeacher = "先生",
                aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")
            dao.upsertLesson(row); dao.upsertDayType(DayTypeEntity(date, DayType.A))
            dao.upsertTask(TaskEntity(id = 93, lessonId = 71, subject = "手入力", title = "課題", dueDate = date, createdDate = date))
            val coordinator = SktjfApplyCoordinator(db, repository)
            val stale = (coordinator.preview(document()) as SktjfPreviewResult.Ready).preview
            dao.upsertLesson(row.copy(weeklySubject = "後から編集"))
            assertEquals(SktjfApplyResult.Stale, coordinator.apply(stale, emptyMap(), emptyMap()))
            assertTrue(dao.getSktjfSourceStates().isEmpty())
            assertEquals(DayType.A, dao.getDayType(date)!!.dayType)
            val preview = (coordinator.preview(document()) as SktjfPreviewResult.Ready).preview
            assertEquals(1, preview.lessonConflicts.size); assertEquals(1, preview.calendarConflicts.size)
            assertEquals(SktjfApplyResult.Blocked, coordinator.apply(preview, emptyMap(), mapOf(date to SktjfConflictChoice.APPLY_INCOMING)))
            assertTrue(dao.getSktjfSourceStates().isEmpty())
            assertEquals(DayType.A, dao.getDayType(date)!!.dayType)
            assertEquals("後から編集", dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(preview,
                mapOf("lesson-1" to SktjfConflictChoice.KEEP_LOCAL), mapOf(date to SktjfConflictChoice.APPLY_INCOMING)))
            assertEquals("後から編集", dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
            assertEquals(71L, dao.getTaskById(93)!!.lessonId)
            assertEquals(DayType.B, dao.getDayType(date)!!.dayType)
            assertFalse(dao.getSktjfSourceStates().single().requiresRelinking)
        } finally { db.close() }
    }

    @Test fun conditionalLessonReplacesConflictingRowAndRetainsItsIdentityAcrossUpdates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao(); val repository = SchedulerRepository(db, UiDesignPreferences(context))
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", true, false)))
            dao.upsertLesson(LessonEntity(id = 71, academicYear = 2026, timetableTerm = TimetableTerm.FIRST, dayOfWeek = 1,
                slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "手入力", weeklyTeacher = "先生",
                aSubject = "", aTeacher = "", bSubject = "", bTeacher = ""))
            dao.upsertDayType(DayTypeEntity(date, DayType.A))
            val normal = document().let { doc -> doc.copy(lessons = doc.lessons.map {
                it.copy(conditions = SktjfConditions(emptySet(), setOf("DD")))
            }) }; val dd = normal.lessons.single().copy(id = "dd-1", subject = "DD授業",
                conditions = SktjfConditions(setOf("DD"), emptySet()))
            val incoming = normal.copy(lessons = normal.lessons + dd)
            val coordinator = SktjfApplyCoordinator(db, repository)
            val preview = (coordinator.preview(incoming) as SktjfPreviewResult.Ready).preview
            assertEquals(1, preview.lessonConflicts.size)
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(preview,
                mapOf("dd-1" to SktjfConflictChoice.APPLY_INCOMING),
                mapOf(date to SktjfConflictChoice.APPLY_INCOMING)))
            assertEquals("DD授業", dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
            assertEquals(71L, dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.id)
            assertTrue(dao.getSktjfExtraLessons().isEmpty())
            val revised = incoming.copy(revision = "2026.S.1", lessons = listOf(incoming.lessons.first(), dd.copy(subject = "改訂DD授業")))
            val next = (coordinator.preview(revised) as SktjfPreviewResult.Ready).preview
            assertTrue(next.lessonConflicts.isEmpty()); assertTrue(next.calendarConflicts.isEmpty())
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(next, emptyMap(), emptyMap()))
            assertEquals(71L, dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.id)
            assertEquals("改訂DD授業", dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
            assertTrue(dao.getSktjfExtraLessons().isEmpty())
        } finally { db.close() }
    }

    @Test fun chosenCoursesApplyAndReapplyWithoutDuplicatingOrLosingUserEdits() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val original = document()
            val first = original.lessons.single().copy(id = "a1", cycle = SktjfCycle.A, subject = "A候補1")
            val second = first.copy(id = "a2", subject = "A候補2")
            val b = first.copy(id = "b", cycle = SktjfCycle.B, subject = "B授業")
            val incoming = original.copy(lessons = listOf(first, second, b))
            val coordinator = SktjfApplyCoordinator(db, SchedulerRepository(db, UiDesignPreferences(context)))
            assertEquals(SktjfPreviewResult.Blocked, coordinator.preview(incoming))
            val selected = requireNotNull(selectSktjfLessons(incoming, emptySet(), setOf("a2", "b")))
            val preview = (coordinator.preview(selected) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(preview, emptyMap(), emptyMap()))
            val row = dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!
            assertEquals("A候補2", row.aSubject); assertEquals("B授業", row.bSubject)
            dao.upsertTask(TaskEntity(id = 94, lessonId = row.id, subject = "A候補2", title = "課題", dueDate = date, createdDate = date))
            dao.upsertLesson(row.copy(aSubject = "手動変更"))
            val again = (coordinator.preview(selected) as SktjfPreviewResult.Ready).preview
            val choices = again.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL }
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(again, choices, emptyMap()))
            val saved = dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!
            assertEquals(row.id, saved.id); assertEquals("手動変更", saved.aSubject); assertEquals("B授業", saved.bSubject)
            assertEquals(row.id, dao.getTaskById(94)!!.lessonId)
            assertTrue(dao.getSktjfExtraLessons().isEmpty())
        } finally { db.close() }
    }

    @Test fun updateProtectsEditsAndManualSlotsAndTracksExcludedChanges() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val coordinator = SktjfApplyCoordinator(db, SchedulerRepository(db, UiDesignPreferences(context)))
            val initial = document().let { doc ->
                val lesson = doc.lessons.single()
                val slot = doc.slotCoverage.single()
                doc.copy(
                    lessons = (1..3).map { day -> lesson.copy(id = "lesson-$day", dayOfWeek = day, subject = "授業$day") },
                    slotCoverage = (1..4).map { day -> slot.copy(dayOfWeek = day,
                        state = if (day == 4) SktjfSlotState.EMPTY else SktjfSlotState.PRESENT) }
                )
            }
            val first = (coordinator.preview(initial) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(first, emptyMap(), emptyMap()))
            val firstRow = dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!
            val secondRow = dao.getLesson(2026, TimetableTerm.FIRST, 2, 0)!!
            val thirdRow = dao.getLesson(2026, TimetableTerm.FIRST, 3, 0)!!
            dao.upsertLesson(thirdRow.copy(weeklySubject = "手動編集"))
            dao.upsertLesson(firstRow.copy(id = 0, dayOfWeek = 4, weeklySubject = "手動追加"))
            dao.upsertTask(TaskEntity(id = 93, lessonId = secondRow.id, subject = "授業2", title = "課題", dueDate = date, createdDate = date))
            val revised = initial.copy(revision = "2026.S.1", lessons = initial.lessons.map {
                when (it.id) {
                    "lesson-1" -> it.copy(subject = "更新1")
                    "lesson-2" -> it.copy(subject = "更新2")
                    else -> it
                }
            })
            val update = (coordinator.preview(revised) as SktjfPreviewResult.Ready).preview
            assertEquals(listOf("lesson-3"), update.lessonConflicts.map { it.sourceLessonId })
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(update,
                mapOf("lesson-1" to SktjfConflictChoice.KEEP_LOCAL, "lesson-3" to SktjfConflictChoice.KEEP_LOCAL), emptyMap()))
            assertEquals("授業1", dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
            assertEquals("更新2", dao.getLesson(2026, TimetableTerm.FIRST, 2, 0)!!.weeklySubject)
            assertEquals(secondRow.id, dao.getLesson(2026, TimetableTerm.FIRST, 2, 0)!!.id)
            assertEquals(secondRow.id, dao.getTaskById(93)!!.lessonId)
            assertEquals("手動編集", dao.getLesson(2026, TimetableTerm.FIRST, 3, 0)!!.weeklySubject)
            assertEquals("手動追加", dao.getLesson(2026, TimetableTerm.FIRST, 4, 0)!!.weeklySubject)
            val again = (coordinator.preview(revised) as SktjfPreviewResult.Ready).preview
            assertEquals(setOf("lesson-1", "lesson-3"), again.lessonConflicts.map { it.sourceLessonId }.toSet())
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(again,
                again.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL }, emptyMap()))
            assertEquals("授業1", dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
            val nextRevision = revised.copy(revision = "2026.S.2", lessons = revised.lessons.map {
                if (it.id == "lesson-1") it.copy(subject = "再更新1") else it
            })
            val next = (coordinator.preview(nextRevision) as SktjfPreviewResult.Ready).preview
            assertEquals(setOf("lesson-1", "lesson-3"), next.lessonConflicts.map { it.sourceLessonId }.toSet())
            assertEquals("授業1", dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(next,
                next.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL }, emptyMap()))

            val removed = nextRevision.copy(revision = "2026.S.3",
                lessons = nextRevision.lessons.filterNot { it.id == "lesson-2" },
                slotCoverage = nextRevision.slotCoverage.map {
                    if (it.dayOfWeek == 2) it.copy(state = SktjfSlotState.EMPTY) else it
                })
            val removal = (coordinator.preview(removed) as SktjfPreviewResult.Ready).preview
            assertEquals(setOf("lesson-1", "lesson-3"), removal.lessonConflicts.map { it.sourceLessonId }.toSet())
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(removal,
                removal.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL }, emptyMap()))
            val cleared = dao.getLesson(2026, TimetableTerm.FIRST, 2, 0)!!
            assertEquals(secondRow.id, cleared.id)
            assertEquals("", cleared.weeklySubject)
            assertEquals(secondRow.id, dao.getTaskById(93)!!.lessonId)
            assertEquals("手動編集", dao.getLesson(2026, TimetableTerm.FIRST, 3, 0)!!.weeklySubject)
            assertEquals("手動追加", dao.getLesson(2026, TimetableTerm.FIRST, 4, 0)!!.weeklySubject)
            val repeatedRemoval = (coordinator.preview(removed) as SktjfPreviewResult.Ready).preview
            assertEquals(setOf("lesson-1", "lesson-3"), repeatedRemoval.lessonConflicts.map { it.sourceLessonId }.toSet())
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(repeatedRemoval,
                repeatedRemoval.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL }, emptyMap()))
            assertEquals(secondRow.id, dao.getTaskById(93)!!.lessonId)
        } finally { db.close() }
    }

    @Test fun importedSpecialLessonsSuppressNotificationsWithoutSuppressingManualLessons() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val incoming = document().let { doc ->
                val lesson = doc.lessons.single()
                val slot = doc.slotCoverage.single()
                doc.copy(
                    lessons = listOf(
                        lesson.copy(subject = "補講", teachers = emptyList(), kind = SktjfLessonKind.MAKEUP),
                        lesson.copy(id = "extension", dayOfWeek = 2, subject = "延長枠", teachers = emptyList(), kind = SktjfLessonKind.EXTENSION)
                    ),
                    slotCoverage = listOf(slot, slot.copy(dayOfWeek = 2), slot.copy(dayOfWeek = 3, state = SktjfSlotState.EMPTY))
                )
            }
            val coordinator = SktjfApplyCoordinator(db, SchedulerRepository(db, UiDesignPreferences(context)))
            val preview = (coordinator.preview(incoming) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(preview, emptyMap(), emptyMap()))
            val makeup = dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!
            dao.upsertLesson(makeup.copy(id = 0, dayOfWeek = 3))

            var schedule = requireNotNull(EffectiveSchoolSchedule.load(dao))
            assertEquals("補講", schedule.lessonsOn(date).single().content.subject)
            assertTrue(schedule.notificationLessonsOn(date).isEmpty())
            assertEquals("延長枠", schedule.lessonsOn(date.plusDays(1)).single().content.subject)
            assertTrue(schedule.notificationLessonsOn(date.plusDays(1)).isEmpty())
            assertEquals("補講", schedule.notificationLessonsOn(date.plusDays(2)).single().content.subject)

            dao.upsertLesson(makeup.copy(weeklySubject = "数学"))
            schedule = requireNotNull(EffectiveSchoolSchedule.load(dao))
            assertEquals("数学", schedule.notificationLessonsOn(date).single().content.subject)
            dao.upsertLesson(makeup)
            dao.upsertUsageMode(UsageModeEntity(mode = "GENERIC"))
            schedule = requireNotNull(EffectiveSchoolSchedule.load(dao))
            assertEquals("補講", schedule.notificationLessonsOn(date).single().content.subject)
        } finally { db.close() }
    }

    @Test fun changingWeeklyToAlternatingCanBeResolvedByReplacingTheSlot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30), enableAbTimetable = true))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            val coordinator = SktjfApplyCoordinator(db, repository)
            val original = document()
            val first = (coordinator.preview(original) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(first, emptyMap(), emptyMap()))
            val id = dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.id
            dao.upsertTask(TaskEntity(id = 93, lessonId = id, subject = "配信授業", title = "課題", dueDate = date, createdDate = date))
            repository.upsertLesson(2026, TimetableTerm.FIRST, 1, 0,
                LessonDraft(mode = LessonMode.ALTERNATING, aSubject = "手動A", bSubject = "手動B"))
            val revised = original.copy(revision = "2026.S.1", lessons = original.lessons.map { it.copy(subject = "配信の更新") })
            val update = (coordinator.preview(revised) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfConflictReason.OVERLAPPING_LOCAL, update.lessonConflicts.single().reason)
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(update,
                mapOf("lesson-1" to SktjfConflictChoice.APPLY_INCOMING), emptyMap()))
            val result = dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!
            assertEquals(id, result.id)
            assertEquals(LessonMode.WEEKLY, result.mode)
            assertEquals("配信の更新", result.weeklySubject)
            assertEquals(id, dao.getTaskById(93)!!.lessonId)
        } finally { db.close() }
    }

    @Test fun editingLessonFollowsOnlyLinkedMatchingCycleAndRequiresConsentForDifferentSubject() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30), enableAbTimetable = true))
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            val row = LessonEntity(id = 71, academicYear = 2026, timetableTerm = TimetableTerm.FIRST, dayOfWeek = 1,
                slotIndex = 0, mode = LessonMode.ALTERNATING, weeklySubject = "", weeklyTeacher = "", aSubject = "英語Ⅱ",
                aTeacher = "", bSubject = "数学I", bTeacher = "")
            dao.upsertLesson(row)
            dao.upsertTask(TaskEntity(id = 91, lessonId = 71, subject = "英語II", title = "課題", dueDate = date, createdDate = date))
            dao.upsertTask(TaskEntity(id = 92, lessonId = 71, subject = "数学Ⅰ", title = "B課題", dueDate = date, createdDate = date))
            dao.upsertTask(TaskEntity(id = 93, lessonId = 72, subject = "英語II", title = "別時限", dueDate = date, createdDate = date))
            dao.upsertTask(TaskEntity(id = 94, subject = "英語II", title = "独立", dueDate = date, createdDate = date))
            dao.upsertPlan(PlanEntity(id = 95, lessonId = 71, subject = "英語Ⅱ", title = "予定", dueDate = date, createdDate = date))
            repository.upsertLesson(2026, TimetableTerm.FIRST, 1, 0,
                LessonDraft(mode = LessonMode.ALTERNATING, aSubject = "英語II（発", bSubject = "数学I"), renameFrom = row)
            assertEquals("英語II", dao.getTaskById(91)!!.subject)
            repository.upsertLesson(2026, TimetableTerm.FIRST, 1, 0,
                LessonDraft(mode = LessonMode.ALTERNATING, aSubject = "英語II（発展）", bSubject = "数学I"), renameFrom = row)
            assertEquals("英語II（発展）", dao.getTaskById(91)!!.subject)
            assertEquals("英語II（発展）", dao.getPlanById(95)!!.subject)
            assertEquals("数学Ⅰ", dao.getTaskById(92)!!.subject)
            assertEquals("英語II", dao.getTaskById(93)!!.subject)
            assertEquals("英語II", dao.getTaskById(94)!!.subject)
            assertEquals("課題", dao.getTaskById(91)!!.title)
            repository.upsertLesson(2026, TimetableTerm.FIRST, 1, 0,
                LessonDraft(mode = LessonMode.ALTERNATING, aSubject = "物理", bSubject = "数学I"))
            assertEquals("英語II（発展）", dao.getTaskById(91)!!.subject)
            repository.upsertLesson(2026, TimetableTerm.FIRST, 1, 0,
                LessonDraft(mode = LessonMode.ALTERNATING, aSubject = "物理", bSubject = "数学I"),
                followDifferentSubject = true, renameFrom = row.copy(aSubject = "英語II（発展）"))
            assertEquals("物理", dao.getTaskById(91)!!.subject)
            assertEquals("物理", dao.getPlanById(95)!!.subject)
            assertEquals("数学Ⅰ", dao.getTaskById(92)!!.subject)
            assertEquals("英語II", dao.getTaskById(93)!!.subject)
        } finally { db.close() }
    }

    @Test fun sameReleaseCanRestoreOrKeepAnIntentionallyDeletedLesson() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val coordinator = SktjfApplyCoordinator(db, SchedulerRepository(db, UiDesignPreferences(context)))
            val doc = document()
            val first = (coordinator.preview(doc) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(first, emptyMap(), emptyMap()))
            val old = dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!
            // Clearing only the subject is enough to remove the visible lesson in the editor.
            dao.upsertLesson(old.copy(weeklySubject = ""))
            val deleted = (coordinator.preview(doc) as SktjfPreviewResult.Ready).preview
            assertTrue(deleted.hasExistingLessons())
            assertEquals(SktjfConflictReason.LOCAL_CHANGED, deleted.lessonConflicts.single().reason)
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(deleted,
                mapOf("lesson-1" to SktjfConflictChoice.KEEP_LOCAL), emptyMap()))
            assertEquals("", dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
            assertEquals(old.weeklyTeacher, dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklyTeacher)
            val restore = (coordinator.preview(doc) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(restore,
                mapOf("lesson-1" to SktjfConflictChoice.APPLY_INCOMING), emptyMap()))
            val restored = dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!
            assertEquals(old.id, restored.id)
            assertEquals("配信授業", restored.weeklySubject)
        } finally { db.close() }
    }

    @Test fun applyingSecondTermKeepsFirstTermAvailableForUpdates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            dao.upsertSettings(SettingsEntity(termStart = LocalDate.of(2026, 4, 1), termEnd = LocalDate.of(2027, 3, 31),
                enableSemesterTimetables = true))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val coordinator = SktjfApplyCoordinator(db, SchedulerRepository(db, UiDesignPreferences(context)))
            val spring = document()
            val fallDate = LocalDate.of(2026, 10, 5)
            val fall = spring.copy(term = "F", revision = "2026.F.0", start = fallDate, end = fallDate,
                calendar = spring.calendar.map { it.copy(date = fallDate) })
            for (doc in listOf(spring, fall)) {
                val preview = (coordinator.preview(doc) as SktjfPreviewResult.Ready).preview
                assertEquals(SktjfApplyResult.Applied, coordinator.apply(preview, emptyMap(), emptyMap()))
            }
            assertEquals(2, dao.getSktjfSourceStates().size)
            assertTrue(dao.getSktjfSourceStates().none { it.requiresRelinking })
            val springAgain = (coordinator.preview(spring) as SktjfPreviewResult.Ready).preview
            assertTrue(springAgain.lessonConflicts.isEmpty())
        } finally { db.close() }
    }

    @Test fun staleSourceCanBeReviewedWithoutOverwritingUserEdits() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val coordinator = SktjfApplyCoordinator(db, SchedulerRepository(db, UiDesignPreferences(context)))
            val first = (coordinator.preview(document()) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(first, emptyMap(), emptyMap()))
            val row = dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!
            dao.upsertLesson(row.copy(weeklySubject = "利用者の授業"))
            dao.requireSktjfRelinking()
            val review = (coordinator.preview(document()) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfConflictReason.OVERLAPPING_LOCAL, review.lessonConflicts.single().reason)
            assertEquals("利用者の授業", dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(review,
                review.lessonConflicts.associate { it.sourceLessonId to SktjfConflictChoice.KEEP_LOCAL },
                review.calendarConflicts.associate { it.date to SktjfConflictChoice.KEEP_LOCAL }))
            assertEquals("利用者の授業", dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
            assertFalse(dao.getSktjfSourceStates().single().requiresRelinking)
        } finally { db.close() }
    }

    @Test fun backupRestorePreservesThreeWayBaselineAndUserEdits() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = source.schedulerDao()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val repository = SchedulerRepository(source, UiDesignPreferences(context))
            val coordinator = SktjfApplyCoordinator(source, repository)
            val initial = document().let { doc ->
                doc.copy(lessons = doc.lessons + doc.lessons.single().copy(id = "lesson-2", dayOfWeek = 2, subject = "別の授業"),
                    slotCoverage = doc.slotCoverage + doc.slotCoverage.single().copy(dayOfWeek = 2))
            }
            val first = (coordinator.preview(initial) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(first, emptyMap(), emptyMap()))
            val edited = dao.getLesson(2026, TimetableTerm.FIRST, 2, 0)!!
            dao.upsertLesson(edited.copy(weeklySubject = "利用者の編集"))
            val backup = repository.exportAllData()
            val restoredRepo = SchedulerRepository(target, UiDesignPreferences(context))
            restoredRepo.importAllData(backup, requireSettings = true)
            val restoredDao = target.schedulerDao()
            assertFalse(restoredDao.getSktjfSourceStates().single().requiresRelinking)
            val revised = initial.copy(revision = "2026.S.1", lessons = initial.lessons.map {
                if (it.id == "lesson-1") it.copy(subject = "配信の更新") else it
            })
            val restoredCoordinator = SktjfApplyCoordinator(target, restoredRepo)
            val update = (restoredCoordinator.preview(revised) as SktjfPreviewResult.Ready).preview
            assertEquals(listOf("lesson-2"), update.lessonConflicts.map { it.sourceLessonId })
            assertEquals(SktjfApplyResult.Applied, restoredCoordinator.apply(update,
                mapOf("lesson-2" to SktjfConflictChoice.KEEP_LOCAL), emptyMap()))
            assertEquals("配信の更新", restoredDao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.weeklySubject)
            assertEquals("利用者の編集", restoredDao.getLesson(2026, TimetableTerm.FIRST, 2, 0)!!.weeklySubject)
        } finally { source.close(); target.close() }
    }

    @Test fun distributionCanChangeWeeklyToAlternatingWithoutLosingTheRow() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30), enableAbTimetable = true))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val coordinator = SktjfApplyCoordinator(db, SchedulerRepository(db, UiDesignPreferences(context)))
            val first = document()
            val initial = (coordinator.preview(first) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(initial, emptyMap(), emptyMap()))
            val id = dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!.id
            dao.upsertTask(TaskEntity(id = 93, lessonId = id, subject = "配信授業", title = "課題", dueDate = date, createdDate = date))
            val original = first.lessons.single()
            val revised = first.copy(revision = "2026.S.1", lessons = listOf(
                original.copy(id = "new-a", cycle = SktjfCycle.A, subject = "A授業"),
                original.copy(id = "new-b", cycle = SktjfCycle.B, subject = "B授業")))
            val update = (coordinator.preview(revised) as SktjfPreviewResult.Ready).preview
            assertTrue(update.lessonConflicts.isEmpty())
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(update, emptyMap(), emptyMap()))
            val row = dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!
            assertEquals(id, row.id); assertEquals(LessonMode.ALTERNATING, row.mode)
            assertEquals("A授業", row.aSubject); assertEquals("B授業", row.bSubject)
            assertEquals(id, dao.getTaskById(93)!!.lessonId)
            val again = (coordinator.preview(revised) as SktjfPreviewResult.Ready).preview
            assertTrue(again.lessonConflicts.isEmpty())
        } finally { db.close() }
    }

    @Test fun manualLessonInOriginallyEmptySlotStaysProtectedOnRepeatedReviews() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val coordinator = SktjfApplyCoordinator(db, SchedulerRepository(db, UiDesignPreferences(context)))
            val first = document().let { it.copy(slotCoverage = it.slotCoverage + it.slotCoverage.single().copy(dayOfWeek = 2, state = SktjfSlotState.EMPTY)) }
            val initial = (coordinator.preview(first) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(initial, emptyMap(), emptyMap()))
            val sourceRow = dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!
            dao.upsertLesson(sourceRow.copy(id = 0, dayOfWeek = 2, weeklySubject = "個人選択"))
            val manualId = dao.getLesson(2026, TimetableTerm.FIRST, 2, 0)!!.id
            dao.upsertTask(TaskEntity(id = 93, lessonId = manualId, subject = "個人選択", title = "課題", dueDate = date, createdDate = date))
            val added = first.lessons.single().copy(id = "new-lesson", dayOfWeek = 2, subject = "新しい配信授業")
            val revised = first.copy(revision = "2026.S.1", lessons = first.lessons + added,
                slotCoverage = first.slotCoverage.map { if (it.dayOfWeek == 2) it.copy(state = SktjfSlotState.PRESENT) else it })
            val preview = (coordinator.preview(revised) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfConflictReason.OVERLAPPING_LOCAL, preview.lessonConflicts.single().reason)
            assertEquals(SktjfConflictChoice.KEEP_LOCAL, jp.linkserver.nittcsc.ui.initialSktjfLessonChoices(preview, false)["new-lesson"])
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(preview,
                mapOf("new-lesson" to SktjfConflictChoice.KEEP_LOCAL), emptyMap()))
            val repeated = (coordinator.preview(revised) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfConflictChoice.KEEP_LOCAL, jp.linkserver.nittcsc.ui.initialSktjfLessonChoices(repeated, false)["new-lesson"])
            assertEquals(SktjfConflictChoice.KEEP_LOCAL, jp.linkserver.nittcsc.ui.initialSktjfLessonChoices(repeated, true)["new-lesson"])
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(repeated,
                mapOf("new-lesson" to SktjfConflictChoice.APPLY_INCOMING), emptyMap()))
            assertEquals("新しい配信授業", dao.getLesson(2026, TimetableTerm.FIRST, 2, 0)!!.weeklySubject)
            assertEquals(manualId, dao.getTaskById(93)!!.lessonId)
        } finally { db.close() }
    }

    @Test fun editedAlternatingSlotCanBeKeptOrReplacedByWeeklyDistribution() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30), enableAbTimetable = true))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val coordinator = SktjfApplyCoordinator(db, SchedulerRepository(db, UiDesignPreferences(context)))
            val base = document(); val original = base.lessons.single()
            val first = base.copy(lessons = listOf(
                original.copy(id = "old-a", cycle = SktjfCycle.A, subject = "A授業"),
                original.copy(id = "old-b", cycle = SktjfCycle.B, subject = "B授業")))
            val initial = (coordinator.preview(first) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(initial, emptyMap(), emptyMap()))
            val row = dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!
            dao.upsertTask(TaskEntity(id = 93, lessonId = row.id, subject = "B授業", title = "課題", dueDate = date, createdDate = date))
            dao.upsertLesson(row.copy(bSubject = "手動B"))
            val revised = base.copy(revision = "2026.S.1", lessons = listOf(original.copy(id = "new-weekly", subject = "毎週授業")))
            val update = (coordinator.preview(revised) as SktjfPreviewResult.Ready).preview
            val keep = jp.linkserver.nittcsc.ui.initialSktjfLessonChoices(update, automaticUpdate = true)
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(update, keep, emptyMap()))
            val kept = dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!
            assertEquals(LessonMode.ALTERNATING, kept.mode); assertEquals("A授業", kept.aSubject); assertEquals("手動B", kept.bSubject)
            val again = (coordinator.preview(revised) as SktjfPreviewResult.Ready).preview
            val replace = jp.linkserver.nittcsc.ui.initialSktjfLessonChoices(again, automaticUpdate = false)
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(again, replace, emptyMap()))
            val changed = dao.getLesson(2026, TimetableTerm.FIRST, 1, 0)!!
            assertEquals(row.id, changed.id); assertEquals(LessonMode.WEEKLY, changed.mode)
            assertEquals("毎週授業", changed.weeklySubject); assertEquals(row.id, dao.getTaskById(93)!!.lessonId)
        } finally { db.close() }
    }

    @Test fun extraEditUpdatesOnlyThatLessonAndRejectsStaleEdit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao(); val repository = SchedulerRepository(db, UiDesignPreferences(context))
            val profile = NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", true, false)
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC")); dao.upsertNittcProfiles(listOf(profile))
            val content = SktjfLessonContent(1, 1, SktjfCycle.A, "DD授業", listOf("先生"), emptyList(),
                SktjfConditions(setOf("DD"), emptySet()), SktjfLessonKind.NORMAL, null)
            val json = SktjfBaselineCodec.encode(SktjfStoredBaseline(SktjfBaseline(2026, "S", "1-1", mapOf("extra-1" to content)),
                SktjfCalendarBaseline(2026, "S", "1-1", emptyMap())))
            val source = SktjfExtraLessonEntity("extra-1", 2026, "S", "1-1", "source-1", json)
            dao.upsertSktjfExtraLessons(listOf(source))
            val expected = SktjfExtraLessonResolver.create(SchedulerUsageMode.NITTC, listOf(profile), listOf(source))
                .lessonsOn(date, 1, DayTypeEntity(date, DayType.A), true, 10, 1).single()
            assertTrue(repository.editSktjfExtraLesson(expected, SktjfExtraLessonDraft("変更後", "新しい先生", "教室")))
            val saved = dao.getSktjfExtraLesson("extra-1")!!
            assertEquals("source-1", saved.sourceLessonId)
            assertEquals("変更後", SktjfBaselineCodec.decode(saved.contentJson).lessons.lessons.getValue("extra-1").subject)
            assertFalse(repository.editSktjfExtraLesson(expected, SktjfExtraLessonDraft("古い変更", "", "")))
            assertEquals(saved, dao.getSktjfExtraLesson("extra-1"))
            assertTrue(dao.getAllSyncDatasetMeta().any { it.datasetKey == LessonSyncPartition(2026, TimetableTerm.FIRST).datasetKey })
        } finally { db.close() }
    }

    @Test fun mixedEventIsSavedWithTheRegularDayAndReopenedWithoutConflict() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            dao.upsertDayType(DayTypeEntity(date, DayType.A))
            val initial = document()
            val incoming = initial.copy(calendar = listOf(initial.calendar.single().copy(
                event = SktjfEvent("清掃", SktjfEventKind.OTHER))))
            val coordinator = SktjfApplyCoordinator(db, repository)
            val preview = (coordinator.preview(incoming) as SktjfPreviewResult.Ready).preview
            assertEquals(SktjfCalendarConflictReason.BOTH_CHANGED, preview.calendarConflicts.single().reason)
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(preview, emptyMap(),
                mapOf(date to SktjfConflictChoice.APPLY_INCOMING)))
            assertEquals(DayType.B, dao.getDayType(date)!!.dayType)
            assertEquals("清掃", dao.getDayType(date)!!.schoolEventName)
            assertEquals("OTHER", dao.getDayType(date)!!.schoolEventKind)
            assertEquals("清掃", SktjfBaselineCodec.decode(dao.getSktjfSourceStates().single().baselineJson)
                .calendar.days.getValue(date).event!!.name)
            val reopened = (coordinator.preview(incoming) as SktjfPreviewResult.Ready).preview
            assertTrue(reopened.calendarConflicts.isEmpty())
        } finally { db.close() }
    }

    @Test fun localFileCanBeFollowedByAnOfficialEarlierRevision() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.schedulerDao()
            val repository = SchedulerRepository(db, UiDesignPreferences(context))
            dao.upsertSettings(SettingsEntity(termStart = date, termEnd = date.plusDays(30)))
            dao.upsertUsageMode(UsageModeEntity(mode = "NITTC"))
            dao.upsertNittcProfiles(listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)))
            val coordinator = SktjfApplyCoordinator(db, repository)
            val local = document().copy(revision = "2026.S.5")
            val preview = (coordinator.preview(local) as SktjfPreviewResult.Ready).preview.copy(localFile = true)
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(preview, emptyMap(), emptyMap()))
            assertTrue(dao.getSktjfSourceStates().single().isLocalSktjfSource())
            val official = (coordinator.preview(document()) as SktjfPreviewResult.Ready).preview
            assertTrue(official.lessonConflicts.isEmpty()); assertTrue(official.calendarConflicts.isEmpty())
            assertEquals(SktjfApplyResult.Applied, coordinator.apply(official, emptyMap(), emptyMap()))
            assertFalse(dao.getSktjfSourceStates().single().isLocalSktjfSource())
            assertEquals("2026.S.0", dao.getSktjfSourceStates().single().revision)
        } finally { db.close() }
    }
}
