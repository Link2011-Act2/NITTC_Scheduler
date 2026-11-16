package jp.linkserver.nittcsc.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

enum class NittcYearOutcome { PROMOTED, REPEATED, TRANSFERRED, ADVANCED, GRADUATED, DEFERRED }

@Entity(tableName = "nittc_year_transitions")
data class NittcYearTransitionEntity(@PrimaryKey val academicYear: Int, val outcome: String)

internal fun NittcYearTransitionEntity.validate() {
    require(academicYear in 1900..9998)
    NittcYearOutcome.valueOf(outcome)
}

internal object NittcYearTransitionBackup {
    fun encode(items: List<NittcYearTransitionEntity>): JSONArray {
        validate(items)
        return JSONArray().also { array -> items.sortedBy { it.academicYear }.forEach {
            array.put(JSONObject().put("academicYear", it.academicYear).put("outcome", it.outcome))
        } }
    }

    fun decode(array: JSONArray): List<NittcYearTransitionEntity> {
        require(array.length() <= 200)
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            require(obj.keys().asSequence().toSet() == setOf("academicYear", "outcome"))
            val number = obj.get("academicYear") as? Number ?: error("Invalid year")
            val year = number.toDouble(); require(year.isFinite() && year % 1.0 == 0.0 && year in 1900.0..9998.0)
            val outcome = obj.get("outcome") as? String ?: error("Invalid outcome")
            NittcYearTransitionEntity(year.toInt(), outcome)
        }.also(::validate)
    }

    private fun validate(items: List<NittcYearTransitionEntity>) {
        require(items.size <= 200 && items.map { it.academicYear }.distinct().size == items.size)
        items.forEach { it.validate() }
    }
}
