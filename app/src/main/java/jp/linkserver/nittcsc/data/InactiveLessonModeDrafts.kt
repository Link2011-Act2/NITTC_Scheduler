package jp.linkserver.nittcsc.data

import org.json.JSONArray
import org.json.JSONObject

/** Values left in the editor for the other lesson mode. They do not describe active lessons. */
internal object InactiveLessonModeDrafts {
    private data class Fields(val subject: String, val teacher: String, val location: String?) {
        fun hasData() = subject.isNotBlank() || teacher.isNotBlank() || !location.isNullOrBlank()
    }

    private data class Draft(val mode: LessonMode, val fields: List<Fields>)

    private fun LessonEntity.draft(mode: LessonMode) = Draft(mode, when (mode) {
        LessonMode.WEEKLY -> listOf(Fields(weeklySubject, weeklyTeacher, weeklyLocation))
        LessonMode.ALTERNATING -> listOf(Fields(aSubject, aTeacher, aLocation), Fields(bSubject, bTeacher, bLocation))
    })

    private fun decode(array: JSONArray): List<Draft> = List(array.length()) { index ->
        val entry = array.getJSONArray(index)
        val mode = LessonMode.valueOf(entry.get(0) as String)
        val cycles = if (mode == LessonMode.WEEKLY) 1 else 2
        require(entry.length() == cycles + 1)
        Draft(mode, List(cycles) { cycle ->
            val values = entry.getJSONArray(cycle + 1)
            require(values.length() == 3)
            Fields(values.get(0) as String, values.get(1) as String, if (values.isNull(2)) null else values.get(2) as String)
        })
    }

    private fun encode(drafts: List<Draft>) = JSONArray().also { array ->
        drafts.forEach { draft ->
            array.put(JSONArray().put(draft.mode.name).also { entry ->
                draft.fields.forEach { entry.put(JSONArray().put(it.subject).put(it.teacher).put(it.location ?: JSONObject.NULL)) }
            })
        }
    }

    fun fromBackup(value: JSONArray): String = encode(decode(value).distinct()).toString()

    /** Save displaced values and refill the now-unused editor fields when they are empty. */
    fun preserve(previous: LessonEntity, applied: LessonEntity): LessonEntity {
        if (previous.mode == applied.mode) return applied
        val displaced = previous.draft(applied.mode)
        val drafts = decode(JSONArray(previous.inactiveModeDraftsJson))
        val saved = if (displaced.fields.any { it.hasData() } && displaced != applied.draft(applied.mode))
            drafts.filterNot { it == displaced } + displaced else drafts
        val inactive = applied.draft(previous.mode)
        val restore = saved.lastOrNull { it.mode == inactive.mode }?.takeIf { inactive.fields.none { field -> field.hasData() } }
        val result = when (restore?.mode) {
            LessonMode.WEEKLY -> restore.fields.single().let {
                applied.copy(weeklySubject = it.subject, weeklyTeacher = it.teacher, weeklyLocation = it.location)
            }
            LessonMode.ALTERNATING -> applied.copy(
                aSubject = restore.fields[0].subject, aTeacher = restore.fields[0].teacher, aLocation = restore.fields[0].location,
                bSubject = restore.fields[1].subject, bTeacher = restore.fields[1].teacher, bLocation = restore.fields[1].location)
            null -> applied
        }
        return result.copy(inactiveModeDraftsJson = encode(saved).toString())
    }
}
