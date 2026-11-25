package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.sktjf.parseSktjfRevision
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal object SchoolVacationBackup {
    fun encode(events: List<SchoolVacationEntity>): JSONArray = JSONArray(events.map { event ->
        JSONObject().put("id", event.id).put("revision", event.revision)
            .put("sources", JSONArray(event.sources)).put("base", encodePeriod(event.base))
            .put("local", event.local?.let(::encodePeriod) ?: JSONObject.NULL)
    })

    fun decode(events: JSONArray): List<SchoolVacationEntity> {
        require(events.length() <= 512)
        return List(events.length()) { index ->
            val item = events.getJSONObject(index)
            val id = item.getString("id")
            val revision = item.getString("revision")
            require(id.matches(Regex("[0-9]{4}-(summer|winter|year_end|spring)")) && parseSktjfRevision(revision) != null)
            val sources = item.getJSONArray("sources")
            require(sources.length() in 1..16 && sources.toString().length <= 48_000)
            val base = decodePeriod(item.getJSONObject("base"))
            val local = if (item.isNull("local")) null else decodePeriod(item.getJSONObject("local"))
            SchoolVacationEntity(id, revision, sources.toString(), base, local)
        }.also { require(it.map(SchoolVacationEntity::id).distinct().size == it.size) }
    }

    private fun encodePeriod(period: SchoolVacationPeriod): JSONObject = JSONObject()
        .put("name", period.name).put("start", period.start.toString())
        .put("end", period.end?.toString() ?: JSONObject.NULL)
        .put("confirmedThrough", period.confirmedThrough.toString())
        .put("estimatedEndMonth", period.estimatedEndMonth ?: JSONObject.NULL)

    private fun decodePeriod(item: JSONObject): SchoolVacationPeriod {
        val start = LocalDate.parse(item.getString("start"))
        val end = if (item.isNull("end")) null else LocalDate.parse(item.getString("end"))
        val through = LocalDate.parse(item.getString("confirmedThrough"))
        val estimatedMonth = if (item.isNull("estimatedEndMonth")) null else item.getInt("estimatedEndMonth")
        return SchoolVacationPeriod(item.getString("name"), start, end, through, estimatedMonth).also {
            require(it.isValid())
        }
    }
}

internal fun SchoolVacationPeriod.isValid(): Boolean =
    name.isNotBlank() && name.length <= 128 && start.year in 1900..9999 &&
        ChronoUnit.DAYS.between(start, confirmedThrough) in 0..640 &&
        (end == null || (end == confirmedThrough && estimatedEndMonth == null)) &&
        (estimatedEndMonth == null || estimatedEndMonth in 1..12)
