package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.logic.LessonWeekRange
import jp.linkserver.nittcsc.logic.normalizeSubjectRomanNumerals
import jp.linkserver.nittcsc.logic.OfficialOpportunitySeries
import java.time.DayOfWeek
import java.time.LocalDate

/** A validated candidate cannot be applied through the existing v1 import flow. */
internal data class SktjfV2Candidate(
    val document: SktjfDocument,
    val ranges: Map<String, SktjfV2Range>,
    val weeks: Map<LocalDate, SktjfV2Week>,
    val series: List<SktjfV2WeekSeries> = emptyList(),
    val weekEvidenceComplete: Boolean = true,
)
internal data class SktjfV2Range(val range: LessonWeekRange, val notation: String)
internal data class SktjfV2Week(val number: Int, val sources: List<SktjfReference>)
internal data class SktjfV2WeekSeries(val id: String, val dayOfWeek: Int, val periods: Set<Int>,
    val lessonIds: Set<String>, val cycle: SktjfCycle, val sources: List<SktjfReference>,
    val countingPolicy: SktjfCountingPolicy? = null)

/** Attach the validated ranges before calculating changes; no database is modified. */
internal fun SktjfV2Candidate.documentForMerge(): SktjfDocument =
    document.copy(lessons = document.lessons.map { lesson -> lesson.copy(weekRange = ranges[lesson.id]) })

/** Resolve profile attributes while retaining timed alternatives for date-based resolution. */
internal fun SktjfV2Candidate.forProfile(attributes: Set<String>): SktjfV2Candidate {
    val selected = document.lessonsFor(attributes).map { lesson ->
        lesson.copy(subject = normalizeSubjectRomanNumerals(lesson.subject),
            teachers = lesson.teachers.joinToString("、").let { if (it.isEmpty()) emptyList() else listOf(it) },
            rooms = lesson.rooms.joinToString("、").let { if (it.isEmpty()) emptyList() else listOf(it) },
            conditions = SktjfConditions(emptySet(), emptySet()))
    }
    val ids = selected.map { it.id }.toSet()
    return copy(document = document.copy(lessons = selected), ranges = ranges.filterKeys { it in ids },
        series = series.mapNotNull { item ->
            val members = item.lessonIds.intersect(ids)
            if (members.isEmpty()) null else item.copy(lessonIds = members)
        })
}

/** Preserve source conditions and evidence for comparison; this does not apply lessons. */
internal fun SktjfV2Candidate.sourceBaseline(): SktjfStoredBaseline {
    val lessons = documentForMerge().lessons.associate { lesson -> lesson.id to lesson.content() }
    val calendar = document.calendar.associate { day -> day.date to day.content() }
    return SktjfStoredBaseline(
        SktjfBaseline(document.academicYear, document.term, document.classId, lessons),
        SktjfCalendarBaseline(document.academicYear, document.term, document.classId, calendar),
        weeks = weeks, series = series, termBounds = SktjfTermBounds(document.start, document.end),
    )
}

internal object SktjfV2CandidateJson {
    fun parse(bytes: ByteArray): SktjfV2Candidate = parseInternal(bytes, allowUnconfirmed = false)

    /** For local inspection only; a candidate with incomplete evidence is not applyable. */
    fun inspect(bytes: ByteArray): SktjfV2Candidate = parseInternal(bytes, allowUnconfirmed = true)

    /** v3 verifies the explicit group membership after parsing the common timetable. */
    fun parseForV3(bytes: ByteArray, selectionGroups: List<Set<String>>): SktjfV2Candidate =
        parseInternal(bytes, allowUnconfirmed = false, selectionGroups = selectionGroups)

    private fun parseInternal(bytes: ByteArray, allowUnconfirmed: Boolean, selectionGroups: List<Set<String>> = emptyList()): SktjfV2Candidate {
        val root = strictSktjfObject(bytes, 4 * 1024 * 1024)
        root.fields(setOf("format", "schemaVersion", "schoolId", "academicYear", "term", "classId", "revision", "sources", "periods", "slotCoverage", "lessons", "calendar"), setOf("weekCalendar", "weekSeries"))
        require(root.integer("schemaVersion", 2..2) == 2)
        val ranges = linkedMapOf<String, SktjfV2Range>()
        val lessons = root.getJSONArray("lessons")
        for (index in 0 until lessons.length()) {
            val lesson = lessons.getJSONObject(index)
            if (!lesson.has("activeWeekRange")) continue
            val id = lesson.text("id", 128)
            val value = lesson.getJSONObject("activeWeekRange")
            value.fields(setOf("start", "end", "notation"))
            val start = if (value.isNull("start")) null else value.integer("start", 1..999)
            val end = if (value.isNull("end")) null else value.integer("end", 1..999)
            require(start != null || end != null)
            require(ranges.put(id, SktjfV2Range(LessonWeekRange(start, end), value.text("notation", 200))) == null)
            lesson.remove("activeWeekRange")
        }
        val weekArray = if (root.has("weekCalendar")) root.getJSONArray("weekCalendar") else null
        val seriesArray = if (root.has("weekSeries")) root.getJSONArray("weekSeries") else null
        root.remove("weekCalendar")
        root.remove("weekSeries")
        root.put("schemaVersion", 1)
        val document = SktjfJson.localDocument(root.toString().byteInputStream())
        val sourceIds = document.sources.map { it.id }.toSet()
        val series = mutableListOf<SktjfV2WeekSeries>()
        val assigned = mutableSetOf<String>()
        require((seriesArray?.length() ?: 0) <= 1000)
        for (index in 0 until (seriesArray?.length() ?: 0)) {
            val item = requireNotNull(seriesArray).getJSONObject(index)
            item.fields(setOf("id", "dayOfWeek", "periods", "lessonIds", "cycle", "sources"), setOf("countingPolicy"))
            val id = item.text("id", 128)
            require(Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$").matches(id) && series.none { it.id == id })
            val day = item.integer("dayOfWeek", 1..7)
            val periodArray = item.getJSONArray("periods"); require(periodArray.length() in 1..12)
            val periods = (0 until periodArray.length()).map { i ->
                val value = periodArray.get(i) as? Number ?: error("Invalid period")
                val number = value.toDouble(); require(number.isFinite() && number % 1.0 == 0.0 && number in 1.0..12.0)
                number.toInt()
            }.also { require(it.distinct().size == it.size) }.toSet()
            val membersArray = item.getJSONArray("lessonIds"); require(membersArray.length() in 1..1000)
            val members = (0 until membersArray.length()).map { i ->
                (membersArray.get(i) as? String ?: error("Invalid lesson id")).also { require(assigned.add(it)) }
            }.toSet().also { require(it.size == membersArray.length()) }
            val cycle = SktjfCycle.valueOf(item.text("cycle", 16))
            val mapped = members.map { member -> document.lessons.single { it.id == member } }
            require(mapped.all { it.dayOfWeek == day && it.period in periods &&
                (cycle == SktjfCycle.WEEKLY || it.cycle == SktjfCycle.WEEKLY || it.cycle == cycle) })
            require(periods.all { period -> mapped.any { it.period == period } })
            val references = item.getJSONArray("sources"); require(references.length() in 1..128)
            val sources = (0 until references.length()).map { i ->
                val ref = references.getJSONObject(i); ref.fields(setOf("sourceId", "page"), setOf("note"))
                val source = ref.text("sourceId", 128); require(source in sourceIds)
                SktjfReference(source, ref.integer("page", 1..100_000), ref.optionalText("note"))
            }
            val policy = if (item.has("countingPolicy")) SktjfCountingPolicyJson.decode(item.getJSONObject("countingPolicy")) else null
            if (policy != null) {
                require(policy.sources.all { it.sourceId in sourceIds })
                require(policy.confirmedNonTeachingDates.all { it in document.start..document.end })
                require(policy.officialPeriodOverrides.all { (date, values) -> date in document.start..document.end && values.keys.all { it + 1 in periods } })
            }
            series += SktjfV2WeekSeries(id, day, periods, members, cycle, sources, policy)
        }
        if (series.isNotEmpty()) require(ranges.keys.all { it in assigned })
        val weeks = linkedMapOf<LocalDate, SktjfV2Week>()
        require((weekArray?.length() ?: 0) <= 370)
        for (index in 0 until (weekArray?.length() ?: 0)) {
            val item = requireNotNull(weekArray).getJSONObject(index)
            item.fields(setOf("date", "week", "sources"))
            val day = LocalDate.parse(item.text("date", 10))
            require(day in document.start..document.end)
            val references = item.getJSONArray("sources")
            require(references.length() in 1..128)
            val sources = (0 until references.length()).map { i ->
                val ref = references.getJSONObject(i)
                ref.fields(setOf("sourceId", "page"), setOf("note"))
                val source = ref.text("sourceId", 128); require(source in sourceIds)
                SktjfReference(source, ref.integer("page", 1..100_000), ref.optionalText("note"))
            }
            require(weeks.put(day, SktjfV2Week(item.integer("week", 1..999), sources)) == null)
        }
        require(ranges.keys.all { id -> document.lessons.any { it.id == id } })
        val sourceCalendar = SktjfCalendarBaseline(document.academicYear, document.term, document.classId,
            document.calendar.associate { it.date to it.content() })
        val groupByLesson = series.flatMap { group -> group.lessonIds.map { it to group } }.toMap()
        val completeCounts = series.mapNotNull { group ->
            val policy = group.countingPolicy ?: return@mapNotNull null
            val periods = group.periods.map { it - 1 }.toSet()
            val opportunity = OfficialOpportunitySeries(group.id, DayOfWeek.of(group.dayOfWeek), periods)
            val counts = sktjfOfficialOpportunityOrdinals(sourceCalendar, document.start, document.end, opportunity,
                group.cycle, policy.confirmedNonTeachingDates,
                policy.officialPeriodOverrides.filterValues { it.values.any { value -> value != false } }.keys
            ) { date, content -> policy.decision(date, content, periods) }
            if (counts.values.any { it == null }) return@mapNotNull null
            val resolved = counts.mapValues { requireNotNull(it.value) }
            group.id to resolved
        }.toMap()
        fun countFor(id: String, date: LocalDate): Int? {
            val group = groupByLesson[id]
            return if (group?.countingPolicy != null) completeCounts[group.id]?.get(date) else weeks[date]?.number
        }
        fun explicitlyExcluded(id: String, date: LocalDate): Boolean = groupByLesson[id]?.let { group ->
            completeCounts[group.id]?.let { date !in it } ?: false
        } ?: false
        var weekEvidenceComplete = true
        if (series.any { group -> group.countingPolicy != null && group.lessonIds.any { it in ranges } && group.id !in completeCounts }) {
            weekEvidenceComplete = false
            require(allowUnconfirmed)
        }
        for (lesson in document.lessons.filter { it.id in ranges }) {
            for (day in document.calendar) {
                if (day.dayType == SktjfDayType.HOLIDAY && groupByLesson[lesson.id]?.let { completeCounts[it.id]?.containsKey(day.date) } != true) continue
                val effectiveDay = day.overrideDayOfWeek ?: day.date.dayOfWeek.value
                if (effectiveDay != lesson.dayOfWeek) continue
                val effectiveType = day.overrideDayType ?: day.dayType
                if (lesson.cycle != SktjfCycle.WEEKLY) {
                    if (effectiveType == null) {
                        weekEvidenceComplete = false
                        require(allowUnconfirmed)
                        continue
                    }
                    if (lesson.cycle.name != effectiveType.name) continue
                }
                if (countFor(lesson.id, day.date) == null && !explicitlyExcluded(lesson.id, day.date)) {
                    weekEvidenceComplete = false
                    require(allowUnconfirmed)
                }
            }
        }
        for ((index, a) in document.lessons.withIndex()) for (b in document.lessons.drop(index + 1)) {
            if (selectionGroups.any { a.id in it && b.id in it }) continue
            if (a.id !in ranges && b.id !in ranges) continue
            if (a.dayOfWeek != b.dayOfWeek || a.period != b.period) continue
            if (a.conditions.all.any { it in b.conditions.none } || b.conditions.all.any { it in a.conditions.none }) continue
            for (day in document.calendar) {
                if (day.dayType == SktjfDayType.HOLIDAY && listOf(a, b).none { lesson ->
                        groupByLesson[lesson.id]?.let { completeCounts[it.id]?.containsKey(day.date) } == true
                    }) continue
                if ((day.overrideDayOfWeek ?: day.date.dayOfWeek.value) != a.dayOfWeek) continue
                val cycle = day.overrideDayType ?: day.dayType
                if (listOf(a, b).any { it.cycle != SktjfCycle.WEEKLY && it.cycle.name != cycle?.name }) continue
                fun active(id: String): Boolean? {
                    val range = ranges[id]?.range ?: return true
                    val week = countFor(id, day.date) ?: return if (explicitlyExcluded(id, day.date)) false else null
                    return (range.start == null || week >= range.start) && (range.end == null || week <= range.end)
                }
                require(!(active(a.id) == true && active(b.id) == true))
            }
        }
        return SktjfV2Candidate(document, ranges, weeks, series, weekEvidenceComplete)
    }
}
