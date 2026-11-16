package jp.linkserver.nittcsc.data

import androidx.room.Entity
import jp.linkserver.nittcsc.sktjf.SktjfBaselineCodec
import jp.linkserver.nittcsc.sktjf.SktjfSourceLinks
import org.json.JSONArray
import org.json.JSONObject

/** A restore or sync may change row identities; merging must relink before trusting provenance. */
@Entity(tableName = "sktjf_source_state", primaryKeys = ["academicYear", "term", "classId"])
data class SktjfSourceStateEntity(
    val academicYear: Int,
    val term: String,
    val classId: String,
    val revision: String,
    val baselineJson: String,
    val requiresRelinking: Boolean = true,
    val linksJson: String = "[]"
)

internal object SktjfSourceStateBackup {
    fun validate(state: SktjfSourceStateEntity) {
        val baseline = SktjfBaselineCodec.decode(state.baselineJson)
        require(baseline.lessons.academicYear == state.academicYear && baseline.lessons.term == state.term && baseline.lessons.classId == state.classId)
        require(Regex("^${state.academicYear}\\.${state.term}\\.(0|[1-9][0-9]*)$").matches(state.revision))
        require(SktjfSourceLinks.decode(state.linksJson).all { it.sourceLessonId in baseline.lessons.lessons })
    }

    fun encode(states: List<SktjfSourceStateEntity>): JSONArray {
        require(states.size <= 256 && states.map { Triple(it.academicYear, it.term, it.classId) }.distinct().size == states.size)
        return JSONArray().also { array -> states.forEach { state ->
            validate(state)
            array.put(JSONObject().put("academicYear", state.academicYear).put("term", state.term)
                .put("classId", state.classId).put("revision", state.revision)
                .put("baseline", JSONObject(state.baselineJson)).put("links", JSONArray(state.linksJson)))
        } }
    }

    fun decode(array: JSONArray): List<SktjfSourceStateEntity> {
        require(array.length() <= 256)
        val states = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            require(item.keys().asSequence().toSet() == setOf("academicYear", "term", "classId", "revision", "baseline", "links"))
            val year = item.get("academicYear") as? Number ?: error("Invalid academic year")
            val numericYear = year.toDouble(); require(numericYear.isFinite() && numericYear % 1.0 == 0.0 && numericYear in 1900.0..9998.0)
            fun string(key: String) = item.get(key) as? String ?: error("Invalid value")
            SktjfSourceStateEntity(numericYear.toInt(), string("term"), string("classId"), string("revision"),
                item.getJSONObject("baseline").toString(), requiresRelinking = true, linksJson = item.getJSONArray("links").toString()).also(::validate)
        }
        require(states.map { Triple(it.academicYear, it.term, it.classId) }.distinct().size == states.size)
        return states
    }
}
