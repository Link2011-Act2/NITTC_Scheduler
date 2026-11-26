package jp.linkserver.nittcsc.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import org.json.JSONArray
import org.json.JSONObject

/** The chosen source ID may outlive a removed option so that the user can review it. */
@Entity(tableName = "sktjf_selection_groups", primaryKeys = ["academicYear", "term", "classId", "groupId"])
data class SktjfSelectionGroupEntity(
    val academicYear: Int, val term: String, val classId: String, val groupId: String,
    val dayOfWeek: Int, val period: Int, val lessonIdsJson: String, val sourcesJson: String,
    val selectedLessonId: String?,
    @ColumnInfo(defaultValue = "'[]'") val retiredLessonIdsJson: String = "[]",
    @ColumnInfo(defaultValue = "0") val suspendedByTimetableReplacement: Boolean = false
)

internal object SktjfSelectionGroupBackup {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    private val classId = Regex("^(1-[1-4]|[2-5][MEIB])$")
    fun validate(group: SktjfSelectionGroupEntity) {
        require(group.academicYear in 1900..9998 && group.term in setOf("S", "F") && classId.matches(group.classId))
        require(id.matches(group.groupId) && group.dayOfWeek in 1..5 && group.period in 1..12)
        require(group.selectedLessonId == null || id.matches(group.selectedLessonId))
        val lessons = JSONArray(group.lessonIdsJson); require(lessons.length() in 0..1000)
        val ids = (0 until lessons.length()).map { index -> (lessons.get(index) as? String ?: error("Invalid lesson")) }
        require(ids.all(id::matches) && ids.distinct().size == ids.size)
        val retired = JSONArray(group.retiredLessonIdsJson); require(retired.length() in 0..1000)
        val retiredIds = (0 until retired.length()).map { retired.get(it) as? String ?: error("Invalid retired lesson") }
        require(retiredIds.all(id::matches) && retiredIds.distinct().size == retiredIds.size && retiredIds.none { it in ids })
        val sources = JSONArray(group.sourcesJson); require(sources.length() in 1..128)
        for (index in 0 until sources.length()) {
            val source = sources.getJSONObject(index)
            require(source.keys().asSequence().toSet().let { it == setOf("sourceId", "page") || it == setOf("sourceId", "page", "note") })
            require(id.matches(source.get("sourceId") as? String ?: error("Invalid source")))
            val page = source.get("page") as? Number ?: error("Invalid page")
            require(page.toDouble().let { it.isFinite() && it % 1.0 == 0.0 && it in 1.0..100_000.0 })
            if (source.has("note")) require(source.get("note") is String && source.getString("note").length <= 1024)
        }
    }
    fun encode(groups: List<SktjfSelectionGroupEntity>): JSONArray {
        validateList(groups)
        return JSONArray().also { result -> groups.sortedWith(compareBy({it.academicYear},{it.term},{it.classId},{it.groupId})).forEach { group ->
            result.put(JSONObject().put("academicYear",group.academicYear).put("term",group.term).put("classId",group.classId)
                .put("groupId",group.groupId).put("dayOfWeek",group.dayOfWeek).put("period",group.period)
                .put("lessonIds",JSONArray(group.lessonIdsJson)).put("sources",JSONArray(group.sourcesJson))
                .put("retiredLessonIds",JSONArray(group.retiredLessonIdsJson))
                .put("suspendedByTimetableReplacement",group.suspendedByTimetableReplacement)
                .put("selectedLessonId",group.selectedLessonId ?: JSONObject.NULL))
        } }
    }
    fun decode(array: JSONArray): List<SktjfSelectionGroupEntity> {
        require(array.length() <= 10000)
        val groups = (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val fields = obj.keys().asSequence().toSet()
            val required = setOf("academicYear","term","classId","groupId","dayOfWeek","period","lessonIds","sources","selectedLessonId")
            require(fields.containsAll(required) && (fields - required).all { it in setOf("retiredLessonIds", "suspendedByTimetableReplacement") })
            if (obj.has("suspendedByTimetableReplacement")) require(obj.get("suspendedByTimetableReplacement") is Boolean)
            fun integer(key: String): Int {
                val number=obj.get(key) as? Number ?: error("Invalid integer")
                val value=number.toDouble();require(value.isFinite() && value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble())
                return value.toInt()
            }
            fun text(key: String) = obj.get(key) as? String ?: error("Invalid string")
            SktjfSelectionGroupEntity(integer("academicYear"),text("term"),text("classId"),text("groupId"),
                integer("dayOfWeek"),integer("period"),obj.getJSONArray("lessonIds").toString(),obj.getJSONArray("sources").toString(),
                if(obj.isNull("selectedLessonId")) null else text("selectedLessonId"),
                if(obj.has("retiredLessonIds")) obj.getJSONArray("retiredLessonIds").toString() else "[]",
                if(obj.has("suspendedByTimetableReplacement")) obj.getBoolean("suspendedByTimetableReplacement") else false)
        }
        validateList(groups);return groups
    }
    fun validateList(groups: List<SktjfSelectionGroupEntity>) {
        require(groups.size <= 10000);groups.forEach(::validate)
        require(groups.map { listOf(it.academicYear.toString(),it.term,it.classId,it.groupId) }.distinct().size == groups.size)
        val memberships = groups.flatMap { group ->
            listOf(group.lessonIdsJson, group.retiredLessonIdsJson).flatMap { json ->
                val array = JSONArray(json)
                (0 until array.length()).map { listOf(group.academicYear.toString(), group.term, group.classId, array.getString(it)) }
            }
        }
        require(memberships.distinct().size == memberships.size)
    }
}
