package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.SktjfSelectionGroupBackup
import jp.linkserver.nittcsc.data.SktjfSelectionGroupEntity
import org.json.JSONArray
import org.json.JSONObject

/** Preserve user choices across revisions; removed choices are retained for review. */
internal fun sktjfGroupIds(json: String): Set<String> = JSONArray(json).let { array ->
    (0 until array.length()).map { array.getString(it) }.toSet()
}

/** Exact membership and slot identify a previously catalogued legacy group. */
internal fun matchingSktjfSelectionGroup(group: SktjfSelectionGroup,
    previous: List<SktjfSelectionGroupEntity>): SktjfSelectionGroupEntity? =
    previous.singleOrNull { it.groupId == group.id } ?: previous.singleOrNull {
        it.dayOfWeek == group.dayOfWeek && it.period == group.period &&
            sktjfGroupIds(it.lessonIdsJson) == group.lessonIds.toSet()
    }

internal fun mergeSktjfSelectionGroups(candidate: SktjfV3Candidate, previous: List<SktjfSelectionGroupEntity>,
    newlyRetired: Map<String, Set<String>> = emptyMap()): List<SktjfSelectionGroupEntity> {
    val document=candidate.timetable.document
    SktjfSelectionGroupBackup.validateList(previous)
    require(previous.all { it.academicYear==document.academicYear && it.term==document.term && it.classId==document.classId })
    val usedPrevious = mutableSetOf<String>()
    val allActiveIds = candidate.selectionGroups.flatMap { it.lessonIds }.toSet()
    val incoming=candidate.selectionGroups.map { group ->
        val sources=JSONArray().also { array -> group.sources.forEach { source ->
            array.put(JSONObject().put("sourceId",source.sourceId).put("page",source.page).also { if(source.note!=null)it.put("note",source.note) })
        } }
        val old = matchingSktjfSelectionGroup(group, previous.filterNot { it.groupId in usedPrevious })
        old?.let { usedPrevious += it.groupId }
        val retired = (old?.let { sktjfGroupIds(it.retiredLessonIdsJson) }.orEmpty() +
            newlyRetired[old?.groupId ?: group.id].orEmpty()) - allActiveIds
        SktjfSelectionGroupEntity(document.academicYear,document.term,document.classId,group.id,group.dayOfWeek,group.period,
            JSONArray(group.lessonIds).toString(),sources.toString(),old?.selectedLessonId, JSONArray(retired.sorted()).toString())
    }
    val removed=previous.filter { it.groupId !in usedPrevious }.mapNotNull { group ->
        val retired = (sktjfGroupIds(group.retiredLessonIdsJson) + newlyRetired[group.groupId].orEmpty()) - allActiveIds
        if (group.selectedLessonId == null && retired.isEmpty()) null
        else group.copy(lessonIdsJson="[]", retiredLessonIdsJson=JSONArray(retired.sorted()).toString())
    }
    return (incoming+removed).also(SktjfSelectionGroupBackup::validateList)
}
