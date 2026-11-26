package jp.linkserver.nittcsc.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import jp.linkserver.nittcsc.sktjf.SktjfBaselineCodec
import jp.linkserver.nittcsc.sktjf.SktjfSourceLinks
import jp.linkserver.nittcsc.sktjf.parseSktjfRevision
import org.json.JSONArray
import org.json.JSONObject

/** An untrusted or separately synchronized timetable requires review before using provenance. */
@Entity(tableName = "sktjf_source_state", primaryKeys = ["academicYear", "term", "classId"])
data class SktjfSourceStateEntity(
    val academicYear: Int,
    val term: String,
    val classId: String,
    val revision: String,
    val baselineJson: String,
    val requiresRelinking: Boolean = true,
    val linksJson: String = "[]",
    @ColumnInfo(defaultValue = "'[]'") val replacedTimedSlotsJson: String = "[]"
)

internal fun decodeReplacedTimedSlots(json: String): Set<Pair<Int, Int>> {
    val array = JSONArray(json)
    require(array.length() <= 60)
    val slots = (0 until array.length()).map { index ->
        val pair = array.getJSONArray(index)
        require(pair.length() == 2)
        fun integer(i: Int): Int {
            val value = pair.get(i) as? Number ?: error("Invalid slot")
            val number = value.toDouble()
            require(number.isFinite() && number % 1.0 == 0.0 && number in 1.0..12.0)
            return number.toInt()
        }
        val day = integer(0); val period = integer(1)
        require(day in 1..5 && period in 1..12)
        day to period
    }
    require(slots.distinct().size == slots.size)
    return slots.toSet()
}
internal fun encodeReplacedTimedSlots(slots: Set<Pair<Int, Int>>): String =
    JSONArray().also { out -> slots.sortedWith(compareBy({ it.first }, { it.second })).forEach { (day, period) ->
        require(day in 1..5 && period in 1..12); out.put(JSONArray().put(day).put(period))
    } }.toString()

internal object SktjfSourceStateBackup {
    fun validate(state: SktjfSourceStateEntity) {
        val baseline = SktjfBaselineCodec.decode(state.baselineJson)
        require(baseline.lessons.academicYear == state.academicYear && baseline.lessons.term == state.term && baseline.lessons.classId == state.classId)
        require(parseSktjfRevision(state.revision)?.let { it.year == state.academicYear && it.term == state.term } == true)
        require(SktjfSourceLinks.decode(state.linksJson).all { it.sourceLessonId in baseline.lessons.lessons })
        decodeReplacedTimedSlots(state.replacedTimedSlotsJson)
    }

    fun encode(states: List<SktjfSourceStateEntity>): JSONArray {
        require(states.size <= 256 && states.map { Triple(it.academicYear, it.term, it.classId) }.distinct().size == states.size)
        return JSONArray().also { array -> states.forEach { state ->
            validate(state)
            array.put(JSONObject().put("academicYear", state.academicYear).put("term", state.term)
                .put("classId", state.classId).put("revision", state.revision)
                .put("baseline", JSONObject(state.baselineJson)).put("links", JSONArray(state.linksJson))
                .put("requiresRelinking", state.requiresRelinking).put("replacedTimedSlots", JSONArray(state.replacedTimedSlotsJson)))
        } }
    }

    fun decode(array: JSONArray, preserveLinkage: Boolean = false): List<SktjfSourceStateEntity> {
        require(array.length() <= 256)
        val states = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val required = setOf("academicYear", "term", "classId", "revision", "baseline", "links")
            val keys = item.keys().asSequence().toSet()
            require(keys.containsAll(required) && (keys - required).all { it in setOf("requiresRelinking", "replacedTimedSlots") })
            val year = item.get("academicYear") as? Number ?: error("Invalid academic year")
            val numericYear = year.toDouble(); require(numericYear.isFinite() && numericYear % 1.0 == 0.0 && numericYear in 1900.0..9998.0)
            fun string(key: String) = item.get(key) as? String ?: error("Invalid value")
            val recordedRelinking = if (item.has("requiresRelinking")) item.get("requiresRelinking") as? Boolean
                ?: error("Invalid linkage state") else true
            SktjfSourceStateEntity(numericYear.toInt(), string("term"), string("classId"), string("revision"),
                item.getJSONObject("baseline").toString(), requiresRelinking = !preserveLinkage || recordedRelinking,
                linksJson = item.getJSONArray("links").toString(),
                replacedTimedSlotsJson = if (item.has("replacedTimedSlots")) item.getJSONArray("replacedTimedSlots").toString() else "[]").also(::validate)
        }
        require(states.map { Triple(it.academicYear, it.term, it.classId) }.distinct().size == states.size)
        return states
    }
}
