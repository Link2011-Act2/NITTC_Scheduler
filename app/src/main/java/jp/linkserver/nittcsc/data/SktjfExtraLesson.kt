package jp.linkserver.nittcsc.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import jp.linkserver.nittcsc.sktjf.SktjfBaselineCodec
import org.json.JSONArray
import org.json.JSONObject

/** A stable independent ID allows multiple lessons to occupy the same timetable slot. */
@Entity(tableName = "sktjf_extra_lessons")
data class SktjfExtraLessonEntity(
    @PrimaryKey val id: String,
    val academicYear: Int,
    val term: String,
    val classId: String,
    val sourceLessonId: String?,
    val contentJson: String
)

internal object SktjfExtraLessonBackup {
    private val idPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    fun validate(lesson: SktjfExtraLessonEntity) {
        require(idPattern.matches(lesson.id) && (lesson.sourceLessonId == null || idPattern.matches(lesson.sourceLessonId)))
        val content = SktjfBaselineCodec.decode(lesson.contentJson)
        require(content.lessons.academicYear == lesson.academicYear && content.lessons.term == lesson.term && content.lessons.classId == lesson.classId)
        require(content.lessons.lessons.keys == setOf(lesson.id) && content.calendar.days.isEmpty())
    }

    fun encode(lessons: List<SktjfExtraLessonEntity>): JSONArray {
        validateList(lessons)
        return JSONArray().also { array -> lessons.sortedBy { it.id }.forEach { lesson ->
            array.put(JSONObject().put("id", lesson.id).put("academicYear", lesson.academicYear).put("term", lesson.term)
                .put("classId", lesson.classId).put("sourceLessonId", lesson.sourceLessonId ?: JSONObject.NULL)
                .put("content", JSONObject(lesson.contentJson)))
        } }
    }

    fun decode(array: JSONArray): List<SktjfExtraLessonEntity> {
        require(array.length() <= 10000)
        val lessons = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            require(item.keys().asSequence().toSet() == setOf("id", "academicYear", "term", "classId", "sourceLessonId", "content"))
            fun string(key: String) = item.get(key) as? String ?: error("Invalid value")
            val number = item.get("academicYear") as? Number ?: error("Invalid year")
            val year = number.toDouble(); require(year.isFinite() && year % 1.0 == 0.0 && year in 1900.0..9998.0)
            SktjfExtraLessonEntity(string("id"), year.toInt(), string("term"), string("classId"),
                if (item.isNull("sourceLessonId")) null else string("sourceLessonId"), item.getJSONObject("content").toString())
        }
        validateList(lessons); return lessons
    }

    fun validateList(lessons: List<SktjfExtraLessonEntity>) {
        require(lessons.size <= 10000)
        lessons.forEach(::validate)
        require(lessons.map { it.id }.distinct().size == lessons.size)
        val sources = lessons.filter { it.sourceLessonId != null }.map { listOf(it.academicYear.toString(), it.term, it.classId, it.sourceLessonId) }
        require(sources.distinct().size == sources.size)
    }
}
