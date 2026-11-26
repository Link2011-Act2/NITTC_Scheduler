package jp.linkserver.nittcsc.sktjf

/** A group is explicit in the source data; a collision alone never creates one. */
internal data class SktjfSelectionGroup(
    val id: String, val dayOfWeek: Int, val period: Int,
    val lessonIds: List<String>, val sources: List<SktjfReference>,
)
internal data class SktjfV3Candidate(val timetable: SktjfV2Candidate, val selectionGroups: List<SktjfSelectionGroup>)

internal object SktjfV3CandidateJson {
    fun parse(bytes: ByteArray): SktjfV3Candidate {
        val root = strictSktjfObject(bytes, 4 * 1024 * 1024)
        root.fields(setOf("format", "schemaVersion", "schoolId", "academicYear", "term", "classId", "revision", "sources", "periods", "slotCoverage", "lessons", "calendar"),
            setOf("weekCalendar", "weekSeries", "selectionGroups"))
        require(root.integer("schemaVersion", 3..3) == 3)
        val groupArray = if (root.has("selectionGroups")) root.getJSONArray("selectionGroups") else null
        root.remove("selectionGroups")
        root.put("schemaVersion", 2)
        val claimedGroups = groupArray?.objects(1000).orEmpty().map { item ->
            item.getJSONArray("lessonIds").strings(1000, 128).toSet()
        }
        val timetable = SktjfV2CandidateJson.parseForV3(root.toString().toByteArray(Charsets.UTF_8), claimedGroups)
        val sources = timetable.document.sources.map { it.id }.toSet()
        val lessons = timetable.document.lessons.associateBy { it.id }
        val assigned = mutableSetOf<String>()
        val groupIds = mutableSetOf<String>()
        val groups = groupArray?.objects(1000).orEmpty().map { item ->
            item.fields(setOf("id", "dayOfWeek", "period", "lessonIds", "sources"))
            val id = item.text("id", 128)
            require(Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$").matches(id) && groupIds.add(id))
            val day = item.integer("dayOfWeek", 1..5)
            val period = item.integer("period", 1..12)
            val ids = item.getJSONArray("lessonIds").strings(1000, 128)
            require(ids.size >= 2 && ids.distinct().size == ids.size && ids.all { assigned.add(it) })
            val members = ids.map { requireNotNull(lessons[it]) }
            require(members.all { it.dayOfWeek == day && it.period == period } && members.map { it.cycle }.distinct().size == 1)
            val refs = item.getJSONArray("sources").objects(128).map { ref ->
                ref.fields(setOf("sourceId", "page"), setOf("note"))
                val source = ref.text("sourceId", 128); require(source in sources)
                SktjfReference(source, ref.integer("page", 1..100_000), ref.optionalText("note"))
            }
            require(refs.isNotEmpty())
            SktjfSelectionGroup(id, day, period, ids, refs)
        }
        return SktjfV3Candidate(timetable, groups)
    }
}
