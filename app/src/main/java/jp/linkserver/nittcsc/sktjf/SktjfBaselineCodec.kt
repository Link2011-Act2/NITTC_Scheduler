package jp.linkserver.nittcsc.sktjf

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Persist source values separately from device-specific lesson row IDs. */
internal data class SktjfStoredBaseline(val lessons: SktjfBaseline, val calendar: SktjfCalendarBaseline, val localFile: Boolean = false)

internal object SktjfBaselineCodec {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private val idPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    private val classPattern = Regex("^(1-[1-4]|[2-5][MEIB]|AC-[A-Z0-9]+)$")

    fun encode(value: SktjfStoredBaseline): String {
        val lessons = value.lessons; val calendar = value.calendar
        require(lessons.academicYear == calendar.academicYear && lessons.term == calendar.term && lessons.classId == calendar.classId)
        val root = JSONObject().put("version", 1).put("academicYear", lessons.academicYear)
            .put("term", lessons.term).put("classId", lessons.classId)
        if (value.localFile) root.put("localFile", true)
        root.put("lessons", JSONArray().also { array ->
            lessons.lessons.toSortedMap().forEach { (id, c) ->
                array.put(JSONObject().put("id", id).put("day", c.dayOfWeek).put("period", c.period)
                    .put("cycle", c.cycle.name).put("subject", c.subject)
                    .put("teachers", JSONArray(c.teachers)).put("rooms", JSONArray(c.rooms))
                    .put("all", JSONArray(c.conditions.all.sorted())).put("none", JSONArray(c.conditions.none.sorted()))
                    .put("kind", c.kind.name).put("remote", c.remote ?: JSONObject.NULL))
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
        return root.toString().also { decode(it) }
    }

    fun decode(json: String): SktjfStoredBaseline {
        val root = strictSktjfObject(json.toByteArray(Charsets.UTF_8), MAX_BYTES)
        val keys = root.keys().asSequence().toSet()
        val required = setOf("version", "academicYear", "term", "classId", "lessons", "calendar")
        require(keys == required || keys == required + "localFile")
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
        require(integer(root, "version", 1..1) == 1)
        val year = integer(root, "academicYear", 1900..9998)
        val term = text(root, "term").also { require(it == "S" || it == "F") }
        val classId = text(root, "classId", 128).also { require(classPattern.matches(it)) }
        val lessons = linkedMapOf<String, SktjfLessonContent>()
        val lessonArray = root.getJSONArray("lessons"); require(lessonArray.length() <= 1000)
        for (i in 0 until lessonArray.length()) {
            val item = lessonArray.getJSONObject(i)
            fields(item, "id", "day", "period", "cycle", "subject", "teachers", "rooms", "all", "none", "kind", "remote")
            val id = text(item, "id", 128).also { require(idPattern.matches(it)) }
            val all = strings(item, "all", true).toSet(); val none = strings(item, "none", true).toSet()
            require(all.none { it in none })
            val remote = if (item.isNull("remote")) null else item.get("remote") as? Boolean ?: error("Invalid remote")
            val content = SktjfLessonContent(integer(item, "day", 1..7), integer(item, "period", 1..12),
                SktjfCycle.valueOf(text(item, "cycle")), text(item, "subject"), strings(item, "teachers"), strings(item, "rooms"),
                SktjfConditions(all, none), SktjfLessonKind.valueOf(text(item, "kind")), remote)
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
        return SktjfStoredBaseline(SktjfBaseline(year, term, classId, lessons), SktjfCalendarBaseline(year, term, classId, days), localFile)
    }
}
