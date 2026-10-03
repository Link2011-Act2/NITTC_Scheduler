package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.logic.QrShareCodec
import jp.linkserver.nittcsc.logic.QrShareException
import jp.linkserver.nittcsc.logic.QrShareFailure
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.logic.validSecondTermStart
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class QrShareSection {
    FIRST, SECOND, CANCELLATIONS, CHANGES, DAY_TYPES, NOTES, PLANS, TASKS, EXAMS
}

data class QrShareSelection(
    val academicYear: Int,
    val sections: Set<QrShareSection>,
    val noteKeys: Set<Pair<LocalDate, Int>> = emptySet(),
    val planIds: Set<Long> = emptySet(),
    val taskIds: Set<Long> = emptySet()
)

/** 端末固有IDとリマインダー設定は配布せず、授業の紐付けは位置・内容で復元する。 */
data class QrSharedItem(
    val subject: String,
    val teacher: String?,
    val title: String,
    val description: String?,
    val dueDate: LocalDate,
    val dueHour: Int,
    val dueMinute: Int,
    val completed: Boolean,
    val completedDate: LocalDate?,
    val createdDate: LocalDate,
    val priority: Int,
    val useTeacherMatching: Boolean,
    val lessonReference: QrLessonReference? = null
) {
    fun asTask() = TaskEntity(
        subject = subject, teacher = teacher, title = title, description = description,
        dueDate = dueDate, dueHour = dueHour, dueMinute = dueMinute, isCompleted = completed,
        completedDate = completedDate, createdDate = createdDate, priority = priority,
        useTeacherMatching = useTeacherMatching, updatedAt = System.currentTimeMillis()
    )

    fun asPlan() = PlanEntity(
        subject = subject, teacher = teacher, title = title, description = description,
        dueDate = dueDate, dueHour = dueHour, dueMinute = dueMinute, isCompleted = completed,
        completedDate = completedDate, createdDate = createdDate, priority = priority,
        useTeacherMatching = useTeacherMatching, updatedAt = System.currentTimeMillis()
    )
}

data class QrSharePayload(
    val academicYear: Int,
    val sections: Set<QrShareSection>,
    val secondTermStartMonth: Int,
    val secondTermStartDay: Int,
    val classLabel: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lessons: List<LessonEntity> = emptyList(),
    val cancellations: List<CancelledLessonEntity> = emptyList(),
    val changes: List<ChangedLessonEntity> = emptyList(),
    val dayTypes: List<DayTypeEntity> = emptyList(),
    val longBreaks: List<LongBreakEntity> = emptyList(),
    val notes: List<LessonNoteEntity> = emptyList(),
    val plans: List<QrSharedItem> = emptyList(),
    val tasks: List<QrSharedItem> = emptyList(),
    val scheduleTimes: QrScheduleTimes? = null,
    val examDays: List<DayTypeEntity> = emptyList(),
    val examDaySchedules: List<ExamDayScheduleEntity> = emptyList(),
    val examLessons: List<ExamLessonEntity> = emptyList(),
    val formatVersion: Int = QrShareCodec.VERSION
) {
    fun count(section: QrShareSection): Int = when (section) {
        QrShareSection.FIRST -> lessons.count { it.timetableTerm == TimetableTerm.FIRST }
        QrShareSection.SECOND -> lessons.count { it.timetableTerm == TimetableTerm.SECOND }
        QrShareSection.CANCELLATIONS -> cancellations.size
        QrShareSection.CHANGES -> changes.size
        QrShareSection.DAY_TYPES -> dayTypes.size
        QrShareSection.NOTES -> notes.size
        QrShareSection.PLANS -> plans.size
        QrShareSection.TASKS -> tasks.size
        QrShareSection.EXAMS -> (examDays.map { it.date } + examDaySchedules.map { it.date }).distinct().size
    }
}

/** QR専用の小さい配列形式。バックアップJSON・SKTTP同期ペイロードは変更しない。 */
object QrShareJson {
    fun encode(payload: QrSharePayload): String {
        val root = JSONObject()
            .put("format", "SKTTP/QR").put("version", payload.formatVersion)
            .put("year", payload.academicYear).put("label", payload.classLabel)
            .put("created", payload.createdAt)
            .put("semesterStart", JSONArray(listOf(payload.secondTermStartMonth, payload.secondTermStartDay)))
            .put("sections", JSONArray(payload.sections.sortedBy { it.ordinal }.map { it.name }))
        if (QrShareSection.FIRST in payload.sections || QrShareSection.SECOND in payload.sections) {
            if (payload.formatVersion >= 2) {
                val times = requireNotNull(payload.scheduleTimes)
                root.put("scheduleTimes", JSONArray(listOf(times.periodsPerDay, times.periodDurationMin,
                    times.breakBetweenPeriodsMin, times.lunchBreakMin, times.lunchAfterPeriod,
                    times.firstPeriodStartHour, times.firstPeriodStartMinute, times.arrivalHour,
                    times.arrivalMinute, times.departureHour, times.departureMinute)))
            }
            root.put("lessons", rows(payload.lessons) { l -> listOf(
                l.timetableTerm.name, l.dayOfWeek, l.slotIndex, l.mode.name,
                l.weeklySubject, l.weeklyTeacher, l.weeklyLocation,
                l.aSubject, l.aTeacher, l.aLocation, l.bSubject, l.bTeacher, l.bLocation
            ) })
        }
        if (QrShareSection.CANCELLATIONS in payload.sections) root.put("cancellations", rows(payload.cancellations) { listOf(it.date.toString(), it.slotIndex) })
        if (QrShareSection.CHANGES in payload.sections) root.put("changes", rows(payload.changes) { listOf(it.date.toString(), it.slotIndex, it.subject, it.teacher, it.location) })
        if (QrShareSection.DAY_TYPES in payload.sections) {
            root.put("days", rows(payload.dayTypes) { listOf(it.date.toString(), it.dayType.name, it.overrideLessonDayOfWeek, it.overrideLessonDayType?.name, it.holidaySpecialLabel?.name) })
            root.put("breaks", rows(payload.longBreaks) { listOf(it.name, it.startDate.toString(), it.endDate.toString()) })
        }
        if (QrShareSection.NOTES in payload.sections) root.put("notes", rows(payload.notes) { listOf(it.date.toString(), it.slotIndex, it.text) })
        if (QrShareSection.PLANS in payload.sections) root.put("plans", rows(payload.plans) { itemRow(it, payload.formatVersion) })
        if (QrShareSection.TASKS in payload.sections) root.put("tasks", rows(payload.tasks) { itemRow(it, payload.formatVersion) })
        if (QrShareSection.EXAMS in payload.sections) {
            require(payload.formatVersion >= 2)
            root.put("examDays", rows(payload.examDays) { listOf(it.date.toString(), it.holidaySpecialLabel?.name) })
            root.put("examSchedules", rows(payload.examDaySchedules) { listOf(it.date.toString(), it.arrivalHour, it.arrivalMinute, it.examName) })
            root.put("examLessons", rows(payload.examLessons) { listOf(it.date.toString(), it.slotIndex,
                it.startHour, it.startMinute, it.endHour, it.endMinute, it.subject, it.teacher, it.location, it.memo) })
        }
        return root.toString()
    }

    fun decode(json: String): QrSharePayload {
        try {
            require(json.toByteArray(Charsets.UTF_8).size <= QrShareCodec.MAX_JSON_BYTES)
            validateNesting(json)
            val root = JSONObject(json)
            require(root.getString("format") == "SKTTP/QR")
            val version = root.strictInt("version")
            if (version !in QrShareCodec.LEGACY_VERSION..QrShareCodec.VERSION) throw QrShareException(QrShareFailure.VERSION)
            val year = root.strictInt("year").also { require(it in 2000..2200) }
            val label = root.getString("label").also { require(it.length <= 60 && '\n' !in it && '\r' !in it) }
            val created = root.getLong("created").also { require(it >= 0) }
            val start = root.getJSONArray("semesterStart").also { require(it.length() == 2) }
            val month = start.number(0)
            val day = start.number(1)
            require(validSecondTermStart(month, day))
            val sectionNames = root.getJSONArray("sections")
            require(sectionNames.length() in 1..QrShareSection.entries.size)
            val sections = (0 until sectionNames.length()).map { QrShareSection.valueOf(sectionNames.getString(it)) }.toSet()
            require(sections.size == sectionNames.length())
            require(version >= 2 || QrShareSection.EXAMS !in sections)
            val allowed = mutableSetOf("format", "version", "year", "label", "created", "semesterStart", "sections")
            fun selected(section: QrShareSection, key: String, size: Int): List<JSONArray> {
                if (section !in sections) return emptyList()
                allowed += key
                return readRows(root, key, size)
            }
            val hasTimetable = QrShareSection.FIRST in sections || QrShareSection.SECOND in sections
            val times = if (hasTimetable && version >= 2) {
                allowed += "scheduleTimes"
                readTimes(root.getJSONArray("scheduleTimes"))
            } else null
            val lessons = if (hasTimetable) {
                allowed += "lessons"
                readRows(root, "lessons", 13).map { row ->
                    val term = TimetableTerm.valueOf(row.getString(0))
                    require((if (term == TimetableTerm.FIRST) QrShareSection.FIRST else QrShareSection.SECOND) in sections)
                    LessonEntity(
                        academicYear = year, timetableTerm = term,
                        dayOfWeek = row.number(1).also { require(it in 1..5) }, slotIndex = row.slot(2),
                        mode = LessonMode.valueOf(row.getString(3)),
                        weeklySubject = row.text(4, 1000), weeklyTeacher = row.text(5, 1000), weeklyLocation = row.optionalText(6, 1000),
                        aSubject = row.text(7, 1000), aTeacher = row.text(8, 1000), aLocation = row.optionalText(9, 1000),
                        bSubject = row.text(10, 1000), bTeacher = row.text(11, 1000), bLocation = row.optionalText(12, 1000)
                    )
                }.also { require(it.map { l -> Triple(l.timetableTerm, l.dayOfWeek, l.slotIndex) }.distinct().size == it.size) }
            } else emptyList()
            val cancellations = selected(QrShareSection.CANCELLATIONS, "cancellations", 2).map {
                CancelledLessonEntity(it.date(0).also { d -> require(academicYearForDate(d) == year) }, it.slot(1))
            }.also { require(it.map { r -> r.date to r.slotIndex }.distinct().size == it.size) }
            val changes = selected(QrShareSection.CHANGES, "changes", 5).map {
                ChangedLessonEntity(it.date(0).also { d -> require(academicYearForDate(d) == year) }, it.slot(1), it.text(2, 1000), it.text(3, 1000), it.optionalText(4, 1000))
            }.also { require(it.map { r -> r.date to r.slotIndex }.distinct().size == it.size) }
            val days = selected(QrShareSection.DAY_TYPES, "days", 5).map {
                val type = DayType.valueOf(it.getString(1))
                val overrideDow = if (it.isNull(2)) null else it.number(2).also { n -> require(n in 1..5) }
                val overrideType = it.optionalText(3, 30)?.let(DayType::valueOf)
                require(overrideType != DayType.HOLIDAY && ((overrideDow == null) == (overrideType == null)))
                val special = it.optionalText(4, 40)?.let(HolidaySpecialLabel::valueOf)
                require(special == null || type == DayType.HOLIDAY)
                DayTypeEntity(it.date(0), type, overrideDow, overrideType, special)
            }.also { require(it.map { d -> d.date }.distinct().size == it.size) }
            val breaks = selected(QrShareSection.DAY_TYPES, "breaks", 3).map {
                LongBreakEntity(name = it.text(0, 1000), startDate = it.date(1), endDate = it.date(2)).also { b -> require(b.startDate <= b.endDate) }
            }
            val notes = selected(QrShareSection.NOTES, "notes", 3).map {
                LessonNoteEntity(it.date(0), it.slot(1), it.text(2, 32000))
            }.also { require(it.map { n -> n.date to n.slotIndex }.distinct().size == it.size) }
            val plans = selected(QrShareSection.PLANS, "plans", if (version >= 2) 13 else 12).map(::readItem)
            val tasks = selected(QrShareSection.TASKS, "tasks", if (version >= 2) 13 else 12).map(::readItem)
            val examDays = selected(QrShareSection.EXAMS, "examDays", 2).map { row ->
                val label = HolidaySpecialLabel.valueOf(row.text(1, 40)).also { require(it.usesExamTimetable) }
                DayTypeEntity(row.date(0).also { require(academicYearForDate(it) == year) }, DayType.HOLIDAY, holidaySpecialLabel = label)
            }.also { require(it.map { row -> row.date }.distinct().size == it.size) }
            val examSchedules = selected(QrShareSection.EXAMS, "examSchedules", 4).map { row ->
                ExamDayScheduleEntity(row.date(0).also { require(academicYearForDate(it) == year) },
                    row.number(1).also { require(it in 0..23) }, row.number(2).also { require(it in 0..59) }, row.text(3, 1000))
            }.also { require(it.map { row -> row.date }.distinct().size == it.size) }
            val examLessons = selected(QrShareSection.EXAMS, "examLessons", 10).map { row ->
                ExamLessonEntity(row.date(0).also { require(academicYearForDate(it) == year) }, row.slot(1),
                    row.number(2).also { require(it in 0..23) }, row.number(3).also { require(it in 0..59) },
                    row.number(4).also { require(it in 0..23) }, row.number(5).also { require(it in 0..59) },
                    row.text(6, 1000), row.text(7, 1000), row.text(8, 1000), row.text(9, 32000)
                ).also { require(it.startHour * 60 + it.startMinute < it.endHour * 60 + it.endMinute) }
            }.also { require(it.map { row -> row.date to row.slotIndex }.distinct().size == it.size) }
            val scheduleDates = examSchedules.map { it.date }.toSet()
            require(examLessons.all { it.date in scheduleDates })
            if (QrShareSection.DAY_TYPES in sections) require(examDays.all { examDay ->
                days.any { it.date == examDay.date && it.dayType == DayType.HOLIDAY && it.holidaySpecialLabel == examDay.holidaySpecialLabel }
            })
            require(root.keys().asSequence().all { it in allowed })
            return QrSharePayload(year, sections, month, day, label, created, lessons, cancellations, changes, days, breaks, notes, plans, tasks,
                times, examDays, examSchedules, examLessons, version)
        } catch (e: QrShareException) {
            throw e
        } catch (_: Exception) {
            throw QrShareException(QrShareFailure.INVALID)
        }
    }

    private fun itemRow(item: QrSharedItem, version: Int): List<Any?> = with(item) {
        val row = listOf(subject, teacher, title, description, dueDate.toString(), dueHour, dueMinute, completed,
            completedDate?.toString(), createdDate.toString(), priority, useTeacherMatching)
        if (version < 2) { require(lessonReference == null); row }
        else row + listOf(lessonReference?.let {
            JSONArray(listOf(it.academicYear, it.timetableTerm.name, it.dayOfWeek, it.slotIndex, it.signature))
        })
    }

    private fun validateNesting(json: String) {
        // root・一覧・行・授業参照の4階層まで。再帰JSONパーサーに深い入力を渡さない。
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in json) {
            if (quoted) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 4) }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
        }
        require(!quoted && depth == 0)
    }

    private fun readItem(r: JSONArray) = QrSharedItem(
        subject = r.text(0, 1000), teacher = r.optionalText(1, 1000), title = r.text(2, 4000), description = r.optionalText(3, 32000),
        dueDate = r.date(4), dueHour = r.number(5).also { require(it in 0..23) }, dueMinute = r.number(6).also { require(it in 0..59) },
        completed = r.get(7).let { require(it is Boolean); it }, completedDate = if (r.isNull(8)) null else r.date(8),
        createdDate = r.date(9), priority = r.number(10).also { require(it in -1..1) },
        useTeacherMatching = r.get(11).let { require(it is Boolean); it },
        lessonReference = if (r.length() < 13 || r.isNull(12)) null else r.getJSONArray(12).let { ref ->
            require(ref.length() == 5)
            QrLessonReference(ref.number(0).also { require(it in 2000..2200) }, TimetableTerm.valueOf(ref.text(1, 10)),
                ref.number(2).also { require(it in 1..5) }, ref.slot(3),
                ref.text(4, 64).also { require(it.matches(Regex("[0-9a-f]{64}"))) })
        }
    )

    private fun readTimes(r: JSONArray): QrScheduleTimes {
        require(r.length() == 11)
        val count = r.number(0).also { require(it in 1..12) }
        val result = QrScheduleTimes(count, r.number(1).also { require(it in 1..1440) },
            r.number(2).also { require(it in 0..1440) }, r.number(3).also { require(it in 0..1440) },
            r.number(4).also { require(it in 0..count) }, r.number(5).also { require(it in 0..23) },
            r.number(6).also { require(it in 0..59) }, r.number(7).also { require(it in -1..23) },
            r.number(8).also { require(it in -1..59) }, r.number(9).also { require(it in -1..23) }, r.number(10).also { require(it in -1..59) })
        require((result.arrivalHour == -1 && result.arrivalMinute == -1) || (result.arrivalHour >= 0 && result.arrivalMinute >= 0))
        require((result.departureHour == -1 && result.departureMinute == -1) || (result.departureHour >= 0 && result.departureMinute >= 0))
        return result
    }

    private fun <T> rows(items: List<T>, row: (T) -> List<Any?>) = JSONArray().also { array ->
        items.forEach { item -> array.put(JSONArray().also { r -> row(item).forEach { r.put(it ?: JSONObject.NULL) } }) }
    }
    private fun readRows(root: JSONObject, key: String, size: Int): List<JSONArray> {
        val array = root.getJSONArray(key)
        require(array.length() <= 4096)
        return (0 until array.length()).map { array.getJSONArray(it).also { r -> require(r.length() == size) } }
    }
    private fun JSONObject.strictInt(key: String): Int = get(key).let { require(it is Int); it }
    private fun JSONArray.number(index: Int): Int = get(index).let { require(it is Int); it }
    private fun JSONArray.slot(index: Int): Int = number(index).also { require(it in 0..11) }
    private fun JSONArray.text(index: Int, limit: Int): String = get(index).let { require(it is String && it.length <= limit); it }
    private fun JSONArray.optionalText(index: Int, limit: Int): String? = if (isNull(index)) null else text(index, limit)
    private fun JSONArray.date(index: Int): LocalDate = LocalDate.parse(text(index, 10)).also { require(it.year in 2000..2201) }
}
