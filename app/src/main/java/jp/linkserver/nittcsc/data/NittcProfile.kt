package jp.linkserver.nittcsc.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

enum class SchedulerUsageMode { GENERIC, NITTC }
enum class NittcProgram { UNDERGRADUATE, ADVANCED }

/** A null mode means that the user has not made the required choice yet. */
@Entity(tableName = "usage_mode")
data class UsageModeEntity(
    @PrimaryKey val id: Int = 1,
    val mode: String? = null,
    val migratedFromLegacy: Boolean = false
)

@Entity(tableName = "nittc_profiles")
data class NittcProfileEntity(
    @PrimaryKey val academicYear: Int,
    val program: String,
    val grade: Int,
    val classId: String,
    val isDd: Boolean,
    val isInternational: Boolean,
    val additionalAttributesJson: String = "[]"
)

internal fun UsageModeEntity.validateMode() {
    require(id == 1 && (mode == null || SchedulerUsageMode.entries.any { it.name == mode }))
}

internal fun NittcProfileEntity.attributes(): Set<String> {
    require(additionalAttributesJson.length <= 8192)
    val array = JSONArray(additionalAttributesJson)
    require(array.length() <= 64)
    val extra = (0 until array.length()).map { index ->
        (array.get(index) as? String)?.also { require(Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$").matches(it)) }
            ?: throw IllegalArgumentException("Invalid attribute")
    }
    require(extra.distinct().size == extra.size && "DD" !in extra && "INTERNATIONAL" !in extra)
    return extra.toSet() + (if (isDd) setOf("DD") else emptySet()) +
        (if (isInternational) setOf("INTERNATIONAL") else emptySet())
}

internal fun NittcProfileEntity.validateProfile() {
    require(academicYear in 1900..9998)
    when (NittcProgram.valueOf(program)) {
        NittcProgram.UNDERGRADUATE -> {
            require(grade in 1..5)
            require(if (grade == 1) Regex("^1-[1-4]$").matches(classId) else Regex("^${grade}[MEIB]$").matches(classId))
        }
        NittcProgram.ADVANCED -> require(grade in 1..2 && Regex("^AC-[A-Z0-9]+$").matches(classId))
    }
    attributes()
}

internal data class ModeProfileBackup(val mode: UsageModeEntity?, val profiles: List<NittcProfileEntity>) {
    fun encode(): JSONObject {
        mode?.validateMode(); profiles.forEach { it.validateProfile() }
        require(profiles.map { it.academicYear }.distinct().size == profiles.size)
        return JSONObject().put("mode", mode?.let {
            JSONObject().put("value", it.mode ?: JSONObject.NULL).put("migratedFromLegacy", it.migratedFromLegacy)
        } ?: JSONObject.NULL).put("profiles", JSONArray().also { array ->
            profiles.sortedBy { it.academicYear }.forEach { p ->
                array.put(JSONObject().put("academicYear", p.academicYear).put("program", p.program)
                    .put("grade", p.grade).put("classId", p.classId).put("isDd", p.isDd)
                    .put("isInternational", p.isInternational).put("additionalAttributes", JSONArray(p.additionalAttributesJson)))
            }
        })
    }

    companion object {
        fun decode(root: JSONObject): ModeProfileBackup {
            require(root.has("mode") && root.has("profiles"))
            val mode = if (root.isNull("mode")) null else root.getJSONObject("mode").let { obj ->
                require(obj.has("value") && obj.has("migratedFromLegacy"))
                val value = if (obj.isNull("value")) null else (obj.get("value") as? String
                    ?: throw IllegalArgumentException("Invalid mode"))
                val legacy = obj.get("migratedFromLegacy") as? Boolean ?: throw IllegalArgumentException("Invalid mode")
                UsageModeEntity(mode = value, migratedFromLegacy = legacy).also { it.validateMode() }
            }
            val array = root.getJSONArray("profiles"); require(array.length() <= 200)
            val profiles = (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                fun int(key: String): Int {
                    val number = obj.get(key) as? Number ?: throw IllegalArgumentException("Invalid number")
                    val value = number.toDouble(); require(value.isFinite() && value % 1.0 == 0.0 && value in 0.0..9998.0)
                    return value.toInt()
                }
                NittcProfileEntity(
                    academicYear = int("academicYear"), program = obj.get("program") as? String ?: throw IllegalArgumentException("Invalid program"),
                    grade = int("grade"), classId = obj.get("classId") as? String ?: throw IllegalArgumentException("Invalid class"),
                    isDd = obj.get("isDd") as? Boolean ?: throw IllegalArgumentException("Invalid attribute"),
                    isInternational = obj.get("isInternational") as? Boolean ?: throw IllegalArgumentException("Invalid attribute"),
                    additionalAttributesJson = obj.getJSONArray("additionalAttributes").toString()
                ).also { it.validateProfile() }
            }
            require(profiles.map { it.academicYear }.distinct().size == profiles.size)
            return ModeProfileBackup(mode, profiles)
        }
    }
}
