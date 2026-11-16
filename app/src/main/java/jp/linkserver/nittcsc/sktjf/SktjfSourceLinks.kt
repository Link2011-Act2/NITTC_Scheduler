package jp.linkserver.nittcsc.sktjf

import org.json.JSONArray
import org.json.JSONObject

/** A slot is stable across backup/restore, unlike a Room row ID. */
internal data class SktjfSourceLink(val sourceLessonId: String, val day: Int, val period: Int, val cycle: SktjfCycle)

internal object SktjfSourceLinks {
    private val sourceId = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")

    fun encode(links: List<SktjfSourceLink>): String {
        validate(links)
        return JSONArray().also { array -> links.sortedBy { it.sourceLessonId }.forEach { link ->
            array.put(JSONObject().put("id", link.sourceLessonId).put("day", link.day).put("period", link.period).put("cycle", link.cycle.name))
        } }.toString()
    }

    fun decode(json: String): List<SktjfSourceLink> {
        require(json.toByteArray(Charsets.UTF_8).size <= 512 * 1024)
        // Reuse the strict object parser to reject duplicate keys even inside the array.
        val root = strictSktjfObject("{\"links\":$json}".toByteArray(Charsets.UTF_8), 512 * 1024 + 16)
        require(root.keys().asSequence().toSet() == setOf("links"))
        val array = root.getJSONArray("links"); require(array.length() <= 1000)
        fun number(obj: JSONObject, key: String, range: IntRange): Int {
            val n = obj.get(key) as? Number ?: error("Invalid slot")
            val d = n.toDouble(); require(d.isFinite() && d % 1.0 == 0.0 && d >= range.first && d <= range.last)
            return d.toInt()
        }
        val links = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            require(item.keys().asSequence().toSet() == setOf("id", "day", "period", "cycle"))
            val id = item.get("id") as? String ?: error("Invalid ID")
            val cycle = item.get("cycle") as? String ?: error("Invalid cycle")
            SktjfSourceLink(id, number(item, "day", 1..7), number(item, "period", 1..12), SktjfCycle.valueOf(cycle))
        }
        validate(links); return links
    }

    private fun validate(links: List<SktjfSourceLink>) {
        require(links.size <= 1000 && links.all { sourceId.matches(it.sourceLessonId) && it.day in 1..7 && it.period in 1..12 })
        require(links.map { it.sourceLessonId }.distinct().size == links.size)
        require(links.map { Triple(it.day, it.period, it.cycle) }.distinct().size == links.size)
        val bySlot = links.groupBy { it.day to it.period }
        require(bySlot.values.none { slot -> slot.any { it.cycle == SktjfCycle.WEEKLY } && slot.size > 1 })
    }
}
