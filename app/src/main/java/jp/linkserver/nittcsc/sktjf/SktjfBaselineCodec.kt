package jp.linkserver.nittcsc.sktjf

import org.json.JSONArray
import org.json.JSONObject
import jp.linkserver.nittcsc.logic.LessonWeekRange
import java.time.LocalDate

/** Persist source values separately from device-specific lesson row IDs. */
internal data class SktjfStoredBaseline(
    val lessons: SktjfBaseline, val calendar: SktjfCalendarBaseline, val localFile: Boolean = false,
    val weeks: Map<LocalDate, SktjfV2Week> = emptyMap(),
    val series: List<SktjfV2WeekSeries> = emptyList(),
    val termBounds: SktjfTermBounds? = null,
)

internal data class SktjfTermBounds(val start: LocalDate, val end: LocalDate)

internal object SktjfBaselineCodec {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private val idPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    private val classPattern = Regex("^(1-[1-4]|[2-5][MEIB]|AC-[A-Z0-9]+)$")

    fun encode(value: SktjfStoredBaseline): String {
        val lessons = value.lessons; val calendar = value.calendar
        require(lessons.academicYear == calendar.academicYear && lessons.term == calendar.term && lessons.classId == calendar.classId)
        val version = if (value.termBounds != null) 4 else if (value.series.isNotEmpty()) 3 else if (value.weeks.isNotEmpty() || lessons.lessons.values.any { it.weekRange != null }) 2 else 1
        val root = JSONObject().put("version", version).put("academicYear", lessons.academicYear)
            .put("term", lessons.term).put("classId", lessons.classId)
        if (value.localFile) root.put("localFile", true)
        value.termBounds?.let { root.put("termBounds", JSONObject().put("start", it.start.toString()).put("end", it.end.toString())) }
        root.put("lessons", JSONArray().also { array ->
            lessons.lessons.toSortedMap().forEach { (id, c) ->
                val item = JSONObject().put("id", id).put("day", c.dayOfWeek).put("period", c.period)
                    .put("cycle", c.cycle.name).put("subject", c.subject)
                    .put("teachers", JSONArray(c.teachers)).put("rooms", JSONArray(c.rooms))
                    .put("all", JSONArray(c.conditions.all.sorted())).put("none", JSONArray(c.conditions.none.sorted()))
                    .put("kind", c.kind.name).put("remote", c.remote ?: JSONObject.NULL)
                c.weekRange?.let { week -> item.put("weekRange", JSONObject()
                    .put("start", week.range.start ?: JSONObject.NULL).put("end", week.range.end ?: JSONObject.NULL)
                    .put("notation", week.notation)) }
                array.put(item)
            }
        })
        root.put("calendar", JSONArray().also { array ->
            calendar.days.toSortedMap().forEach { (date, c) ->
                array.put(JSONObject().put("date", date.toString()).put("dayType", c.dayType?.name ?: JSONObject.NULL)
                    .put("overrideDay", c.overrideDayOfWeek ?: JSONObject.NULL)
                    .put("overrideType", c.overrideDayType?.name ?: JSONObject.NULL)
                    .put("event", c.event?.let { JSONObject().put("name", it.name).put("kind", it.kind?.name ?: JSONObject.NULL) } ?: JSONObject.NULL))
            }
        })
        if (value.weeks.isNotEmpty()) root.put("weekCalendar", JSONArray().also { array ->
            value.weeks.toSortedMap().forEach { (date, week) ->
                array.put(JSONObject().put("date", date.toString()).put("week", week.number).put("sources", JSONArray().also { refs ->
                    week.sources.forEach { ref ->
                        refs.put(JSONObject().put("sourceId", ref.sourceId).put("page", ref.page).also { obj ->
                            if (ref.note != null) obj.put("note", ref.note)
                        })
                    }
                }))
            }
        })
        if (value.series.isNotEmpty()) root.put("weekSeries", JSONArray().also { array ->
            value.series.sortedBy { it.id }.forEach { series ->
                array.put(JSONObject().put("id", series.id).put("dayOfWeek", series.dayOfWeek)
                    .put("periods", JSONArray(series.periods.sorted())).put("lessonIds", JSONArray(series.lessonIds.sorted()))
                    .put("cycle", series.cycle.name).put("sources", JSONArray().also { refs ->
                        series.sources.forEach { ref -> refs.put(JSONObject().put("sourceId", ref.sourceId).put("page", ref.page).also { obj ->
                            if (ref.note != null) obj.put("note", ref.note)
                        }) }
                    }).also { if (series.countingPolicy != null) it.put("countingPolicy", SktjfCountingPolicyJson.encode(series.countingPolicy)) })
            }
        })
        return root.toString().also { decode(it) }
    }

    fun decode(json: String): SktjfStoredBaseline {
        val root = strictSktjfObject(json.toByteArray(Charsets.UTF_8), MAX_BYTES)
        val keys = root.keys().asSequence().toSet()
        val required = setOf("version", "academicYear", "term", "classId", "lessons", "calendar")
        require(keys.containsAll(required) && keys.all { it in required || it == "localFile" || it == "weekCalendar" || it == "weekSeries" || it == "termBounds" })
        val localFile = if (root.has("localFile")) root.get("localFile") as? Boolean ?: error("Invalid origin") else false
        fun integer(obj: JSONObject, key: String, range: IntRange): Int {
            val number = obj.get(key) as? Number ?: error("Invalid integer")
            val value = number.toDouble(); require(value.isFinite() && value % 1.0 == 0.0 && value >= range.first && value <= range.last)
            return value.toInt()
        }
        fun text(obj: JSONObject, key: String, max: Int = 1000): String =
            (obj.get(key) as? String ?: error("Invalid text")).also { require(it.isNotBlank() && it.length <= max) }
        fun strings(obj: JSONObject, key: String, ids: Boolean = false): List<String> {
            val array = obj.getJSONArray(key); require(array.length() <= 64)
            return (0 until array.length()).map { i ->
                (array.get(i) as? String ?: error("Invalid text")).also { require(it.isNotBlank() && it.length <= 1000 && (!ids || idPattern.matches(it))) }
            }.also { if (ids) require(it.distinct().size == it.size) }
        }
        fun fields(obj: JSONObject, vararg keys: String) { require(obj.keys().asSequence().toSet() == keys.toSet()) }
        fun optionalText(obj: JSONObject, key: String) = if (obj.isNull(key)) null else text(obj, key)
        val version = integer(root, "version", 1..4)
        require((version == 4) == root.has("termBounds"))
        require(version >= 2 || !root.has("weekCalendar"))
        require(version >= 3 || !root.has("weekSeries"))
        val year = integer(root, "academicYear", 1900..9998)
        val term = text(root, "term").also { require(it == "S" || it == "F") }
        val bounds = if (root.has("termBounds")) root.getJSONObject("termBounds").let { item ->
            fields(item, "start", "end")
            val start = LocalDate.parse(text(item, "start", 10)); val end = LocalDate.parse(text(item, "end", 10))
            require(start >= LocalDate.of(year, 4, 1) && end <= LocalDate.of(year + 1, 3, 31) && !end.isBefore(start))
            SktjfTermBounds(start, end)
        } else null
        val classId = text(root, "classId", 128).also { require(classPattern.matches(it)) }
        val lessons = linkedMapOf<String, SktjfLessonContent>()
        val lessonArray = root.getJSONArray("lessons"); require(lessonArray.length() <= 1000)
        for (i in 0 until lessonArray.length()) {
            val item = lessonArray.getJSONObject(i)
            val baseKeys = setOf("id", "day", "period", "cycle", "subject", "teachers", "rooms", "all", "none", "kind", "remote")
            val lessonKeys = item.keys().asSequence().toSet()
            require(lessonKeys == baseKeys || lessonKeys == baseKeys + "weekRange")
            require(version >= 2 || !item.has("weekRange"))
            val id = text(item, "id", 128).also { require(idPattern.matches(it)) }
            val all = strings(item, "all", true).toSet(); val none = strings(item, "none", true).toSet()
            require(all.none { it in none })
            val remote = if (item.isNull("remote")) null else item.get("remote") as? Boolean ?: error("Invalid remote")
            val weekRange = if (!item.has("weekRange")) null else item.getJSONObject("weekRange").let { value ->
                fields(value, "start", "end", "notation")
                val start = if (value.isNull("start")) null else integer(value, "start", 1..999)
                val end = if (value.isNull("end")) null else integer(value, "end", 1..999)
                require(start != null || end != null)
                SktjfV2Range(LessonWeekRange(start, end), text(value, "notation", 200))
            }
            val content = SktjfLessonContent(integer(item, "day", 1..7), integer(item, "period", 1..12),
                SktjfCycle.valueOf(text(item, "cycle")), text(item, "subject"), strings(item, "teachers"), strings(item, "rooms"),
                SktjfConditions(all, none), SktjfLessonKind.valueOf(text(item, "kind")), remote, weekRange)
            require(lessons.put(id, content) == null)
        }
        val days = linkedMapOf<LocalDate, SktjfCalendarContent>()
        val calendarArray = root.getJSONArray("calendar"); require(calendarArray.length() <= 366)
        for (i in 0 until calendarArray.length()) {
            val item = calendarArray.getJSONObject(i)
            fields(item, "date", "dayType", "overrideDay", "overrideType", "event")
            val date = LocalDate.parse(text(item, "date")); require(date >= LocalDate.of(year, 4, 1) && date <= LocalDate.of(year + 1, 3, 31))
            val event = if (item.isNull("event")) null else item.getJSONObject("event").let { e ->
                fields(e, "name", "kind"); SktjfEvent(text(e, "name"), optionalText(e, "kind")?.let(SktjfEventKind::valueOf))
            }
            val content = SktjfCalendarContent(optionalText(item, "dayType")?.let(SktjfDayType::valueOf),
                if (item.isNull("overrideDay")) null else integer(item, "overrideDay", 1..7),
                optionalText(item, "overrideType")?.let(SktjfDayType::valueOf), event)
            require(days.put(date, content) == null)
        }
        val weeks = linkedMapOf<LocalDate, SktjfV2Week>()
        if (root.has("weekCalendar")) {
            val array = root.getJSONArray("weekCalendar"); require(array.length() <= 370)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i); fields(item, "date", "week", "sources")
                val date = LocalDate.parse(text(item, "date", 10)); require(date >= LocalDate.of(year, 4, 1) && date <= LocalDate.of(year + 1, 3, 31))
                val refs = item.getJSONArray("sources"); require(refs.length() in 1..128)
                val sources = (0 until refs.length()).map { index ->
                    val ref = refs.getJSONObject(index); val refKeys = ref.keys().asSequence().toSet()
                    require(refKeys == setOf("sourceId", "page") || refKeys == setOf("sourceId", "page", "note"))
                    val source = text(ref, "sourceId", 128).also { require(idPattern.matches(it)) }
                    SktjfReference(source, integer(ref, "page", 1..100_000), if (ref.has("note")) text(ref, "note", 1024) else null)
                }
                require(weeks.put(date, SktjfV2Week(integer(item, "week", 1..999), sources)) == null)
            }
        }
        val series = mutableListOf<SktjfV2WeekSeries>()
        val assigned = mutableSetOf<String>()
        if (root.has("weekSeries")) {
            val array = root.getJSONArray("weekSeries"); require(array.length() in 1..1000)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                item.fields(setOf("id", "dayOfWeek", "periods", "lessonIds", "cycle", "sources"), setOf("countingPolicy"))
                val id = text(item, "id", 128).also { require(idPattern.matches(it) && series.none { value -> value.id == it }) }
                val day = integer(item, "dayOfWeek", 1..7)
                val rawPeriods = item.getJSONArray("periods"); require(rawPeriods.length() in 1..12)
                val periods = (0 until rawPeriods.length()).map { index ->
                    val raw = rawPeriods.get(index) as? Number ?: error("Invalid period")
                    val value = raw.toDouble(); require(value.isFinite() && value % 1.0 == 0.0 && value in 1.0..12.0)
                    value.toInt()
                }.also { require(it.distinct().size == it.size) }.toSet()
                val rawMembers = item.getJSONArray("lessonIds"); require(rawMembers.length() in 1..1000)
                val members = (0 until rawMembers.length()).map { index ->
                    (rawMembers.get(index) as? String ?: error("Invalid lesson id")).also { require(assigned.add(it)) }
                }.toSet().also { require(it.size == rawMembers.length()) }
                val cycle = SktjfCycle.valueOf(text(item, "cycle", 16))
                val mapped = members.map { member -> requireNotNull(lessons[member]) }
                require(mapped.all { it.dayOfWeek == day && it.period in periods &&
                    (cycle == SktjfCycle.WEEKLY || it.cycle == SktjfCycle.WEEKLY || it.cycle == cycle) })
                // A profile-filtered v4 snapshot can omit lessons for another profile,
                // while the original source still defines all periods of the opportunity.
                require(version >= 4 || periods.all { period -> mapped.any { it.period == period } })
                val refs = item.getJSONArray("sources"); require(refs.length() in 1..128)
                val sources = (0 until refs.length()).map { index ->
                    val ref = refs.getJSONObject(index); val refKeys = ref.keys().asSequence().toSet()
                    require(refKeys == setOf("sourceId", "page") || refKeys == setOf("sourceId", "page", "note"))
                    val source = text(ref, "sourceId", 128).also { require(idPattern.matches(it)) }
                    SktjfReference(source, integer(ref, "page", 1..100_000), if (ref.has("note")) text(ref, "note", 1024) else null)
                }
                val policy = if (item.has("countingPolicy")) SktjfCountingPolicyJson.decode(item.getJSONObject("countingPolicy")) else null
                if (policy != null) {
                    val yearStart=LocalDate.of(year,4,1);val yearEnd=LocalDate.of(year+1,3,31)
                    require(policy.confirmedNonTeachingDates.all { it in yearStart..yearEnd })
                    require(policy.officialPeriodOverrides.all { (date, values) -> date in yearStart..yearEnd && values.keys.all { it + 1 in periods } })
                }
                series += SktjfV2WeekSeries(id, day, periods, members, cycle, sources, policy)
            }
            require(lessons.filterValues { it.weekRange != null }.keys.all { it in assigned })
        }
        // The merge baseline may retain earlier source dates outside a revised term;
        // counting still uses only the explicitly supplied term boundaries.
        require(bounds == null || (weeks.keys.all { it in bounds.start..bounds.end } &&
            series.all { group -> group.countingPolicy?.let { policy -> policy.confirmedNonTeachingDates.all { it in bounds.start..bounds.end } &&
                policy.officialPeriodOverrides.keys.all { it in bounds.start..bounds.end } } ?: true }))
        require(version == 1 || version == 4 || weeks.isNotEmpty() || lessons.values.any { it.weekRange != null } || series.isNotEmpty())
        require(version != 3 || series.isNotEmpty())
        return SktjfStoredBaseline(SktjfBaseline(year, term, classId, lessons), SktjfCalendarBaseline(year, term, classId, days), localFile, weeks, series, bounds)
    }
}
