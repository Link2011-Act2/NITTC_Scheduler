package jp.linkserver.nittcsc.sktjf

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Shared encoding for the candidate document and stored source snapshot. */
internal object SktjfCountingPolicyJson {
    fun encode(value: SktjfCountingPolicy): JSONObject = JSONObject()
        .put("regularDayCounts", value.regularDayCounts ?: JSONObject.NULL)
        .put("holidayCounts", value.holidayCounts ?: JSONObject.NULL)
        .put("ordinaryPeriodCounts", value.ordinaryPeriodCounts ?: JSONObject.NULL)
        .put("eventCounts", JSONObject().also { out -> value.eventCounts.toSortedMap().forEach { (kind, count) -> out.put(kind.name, count ?: JSONObject.NULL) } })
        .put("officialPeriodOverrides", JSONArray().also { out -> value.officialPeriodOverrides.toSortedMap().forEach { (date, periods) ->
            periods.toSortedMap().forEach { (period, count) -> out.put(JSONObject().put("date", date.toString()).put("period", period + 1).put("counts", count ?: JSONObject.NULL)) }
        } })
        .put("confirmedNonTeachingDates", JSONArray(value.confirmedNonTeachingDates.sorted().map { it.toString() }))
        .put("sources", JSONArray().also { out -> value.sources.forEach { source -> out.put(JSONObject().put("sourceId", source.sourceId)
            .put("page", source.page).also { if (source.note != null) it.put("note", source.note) }) } })

    fun decode(obj: JSONObject): SktjfCountingPolicy {
        obj.fields(setOf("regularDayCounts", "holidayCounts", "eventCounts", "ordinaryPeriodCounts", "officialPeriodOverrides", "confirmedNonTeachingDates", "sources"))
        fun optionalBoolean(value: JSONObject, key: String): Boolean? = if (value.isNull(key)) null else value.get(key) as? Boolean ?: error("Invalid counting rule")
        val rawEvents = obj.getJSONObject("eventCounts")
        val events = rawEvents.keys().asSequence().associate { name -> SktjfEventKind.valueOf(name) to optionalBoolean(rawEvents, name) }
        val overrides = linkedMapOf<LocalDate, MutableMap<Int, Boolean?>>()
        val rawOverrides = obj.getJSONArray("officialPeriodOverrides"); require(rawOverrides.length() <= 4440)
        for (index in 0 until rawOverrides.length()) {
            val item = rawOverrides.getJSONObject(index); item.fields(setOf("date", "period", "counts"))
            val date = LocalDate.parse(item.text("date", 10)); val period = item.integer("period", 1..12) - 1
            val row = overrides.getOrPut(date) { linkedMapOf() }; require(!row.containsKey(period))
            row[period] = optionalBoolean(item, "counts")
        }
        val rawDates = obj.getJSONArray("confirmedNonTeachingDates"); require(rawDates.length() <= 370)
        val dates = (0 until rawDates.length()).map { index ->
            LocalDate.parse(rawDates.get(index) as? String ?: error("Invalid date"))
        }.also { require(it.distinct().size == it.size) }.toSet()
        val rawSources = obj.getJSONArray("sources"); require(rawSources.length() in 1..128)
        val sources = (0 until rawSources.length()).map { index ->
            val ref = rawSources.getJSONObject(index); ref.fields(setOf("sourceId", "page"), setOf("note"))
            SktjfReference(ref.text("sourceId", 128), ref.integer("page", 1..100_000), ref.optionalText("note"))
        }
        return SktjfCountingPolicy(optionalBoolean(obj, "regularDayCounts"), optionalBoolean(obj, "holidayCounts"), events,
            optionalBoolean(obj, "ordinaryPeriodCounts"), overrides, dates, sources)
    }
}
