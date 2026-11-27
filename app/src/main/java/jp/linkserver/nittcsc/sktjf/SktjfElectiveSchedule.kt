package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.logic.OfficialOpportunitySeries
import jp.linkserver.nittcsc.logic.TimetableTerm
import org.json.JSONArray
import java.time.DayOfWeek
import java.time.LocalDate

internal sealed interface SktjfElectiveSlotResult {
    data object Unmanaged : SktjfElectiveSlotResult
    data object Unconfirmed : SktjfElectiveSlotResult
    data class Managed(val group: SktjfSelectionGroupEntity, val options: List<SktjfVisibleExtra>,
        val resolution: SktjfElectiveResolution) : SktjfElectiveSlotResult
}
internal data class SktjfElectiveEditGroup(val group: SktjfSelectionGroupEntity, val options: List<SktjfVisibleExtra>)

/** Resolves explicit groups after the profile, A/B and official teaching opportunity. */
class SktjfElectiveSchedule private constructor(private val groups: List<Entry>) {
    private data class Entry(val group: SktjfSelectionGroupEntity,val ids: List<String>,
        val extras: List<SktjfVisibleExtra>,val source: SktjfStoredBaseline?,val attributes: Set<String>)

    internal fun groupsFor(year: Int, term: TimetableTerm, day: Int, period: Int): List<SktjfElectiveEditGroup> {
        val termCode=if(term==TimetableTerm.FIRST) "S" else "F"
        return groups.filter { it.group.academicYear==year && it.group.term==termCode && it.group.dayOfWeek==day && it.group.period==period }
            .map { entry -> SktjfElectiveEditGroup(entry.group,entry.extras.filter { it.content.conditions.matches(entry.attributes) }) }
            .filter { it.options.size>=2 || (it.group.selectedLessonId!=null && it.options.none { item -> item.sourceLessonId==it.group.selectedLessonId }) }
    }

    internal fun resolve(year: Int, term: TimetableTerm, day: Int, period: Int, date: LocalDate, cycle: SktjfCycle): SktjfElectiveSlotResult {
        val termCode=if(term==TimetableTerm.FIRST) "S" else "F"
        val candidates=groups.filter { it.group.academicYear==year && it.group.term==termCode &&
            it.group.dayOfWeek==day && it.group.period==period }
        // A removed group retains its selection for review, but no longer owns the slot.
        val matching=candidates.filter { entry -> entry.ids.isNotEmpty() && (entry.extras.isEmpty() || entry.extras.any {
            it.content.cycle==SktjfCycle.WEEKLY || it.content.cycle==cycle
        }) }
        if(matching.isEmpty())return SktjfElectiveSlotResult.Unmanaged
        if(matching.size!=1)return SktjfElectiveSlotResult.Unconfirmed
        val entry=matching.single()
        val eligible=mutableListOf<SktjfVisibleExtra>()
        for(extra in entry.extras){
            val content=extra.content
            if(!content.conditions.matches(entry.attributes) || (content.cycle!=SktjfCycle.WEEKLY && content.cycle!=cycle))continue
            val range=content.weekRange?.range
            if(range!=null){
                val source=entry.source ?: return SktjfElectiveSlotResult.Unconfirmed
                val series=source.series.singleOrNull { extra.sourceLessonId in it.lessonIds }
                val number=if(series?.countingPolicy!=null){
                    val bounds=source.termBounds ?: return SktjfElectiveSlotResult.Unconfirmed
                    val policy=series.countingPolicy;val periods=series.periods.map { it-1 }.toSet()
                    val ordinals=sktjfOfficialOpportunityOrdinals(source.calendar,bounds.start,bounds.end,
                        OfficialOpportunitySeries(series.id,DayOfWeek.of(series.dayOfWeek),periods),series.cycle,
                        policy.confirmedNonTeachingDates,policy.officialPeriodOverrides.filterValues { it.values.any { value -> value!=false } }.keys
                    ) { d,dayContent -> policy.decision(d,dayContent,periods) }
                    if(date !in ordinals)continue
                    ordinals[date] ?: return SktjfElectiveSlotResult.Unconfirmed
                } else source.weeks[date]?.number ?: return SktjfElectiveSlotResult.Unconfirmed
                if((range.start!=null && number<range.start)||(range.end!=null && number>range.end))continue
            }
            eligible+=extra
        }
        val ids=eligible.mapNotNull { it.sourceLessonId }
        if(ids.size!=eligible.size || ids.distinct().size!=ids.size)return SktjfElectiveSlotResult.Unconfirmed
        return SktjfElectiveSlotResult.Managed(entry.group,eligible,resolveSktjfElective(entry.ids,ids,entry.group.selectedLessonId))
    }
    companion object {
        internal fun create(mode: SchedulerUsageMode?, profiles: List<NittcProfileEntity>, storedGroups: List<SktjfSelectionGroupEntity>,
            extras: List<SktjfExtraLessonEntity>, states: List<SktjfSourceStateEntity>): SktjfElectiveSchedule {
            if(mode!=SchedulerUsageMode.NITTC)return SktjfElectiveSchedule(emptyList())
            SktjfSelectionGroupBackup.validateList(storedGroups);SktjfExtraLessonBackup.validateList(extras)
            val installed = installedSktjfSources(states)
            val entries=storedGroups.filterNot { it.suspendedByTimetableReplacement }.mapNotNull { group ->
                val profile = profiles.singleOrNull { it.academicYear == group.academicYear } ?: return@mapNotNull null
                val activeClass = installed[group.academicYear to group.term]?.classId ?: profile.classId
                if (activeClass != group.classId) return@mapNotNull null
                val ids=JSONArray(group.lessonIdsJson).let { array -> (0 until array.length()).map { array.getString(it) } }
                val state=states.singleOrNull { it.academicYear==group.academicYear && it.term==group.term && it.classId==group.classId && !it.requiresRelinking }
                val source=state?.let { runCatching { SktjfBaselineCodec.decode(it.baselineJson) }.getOrNull() }
                val items=extras.filter { it.academicYear==group.academicYear && it.term==group.term && it.classId==group.classId && it.sourceLessonId in ids }
                    .map { extra ->
                        val content=SktjfBaselineCodec.decode(extra.contentJson).lessons.lessons.getValue(extra.id)
                        require(content.dayOfWeek==group.dayOfWeek && content.period==group.period)
                        SktjfVisibleExtra(extra.id,extra.sourceLessonId,content,extra.contentJson)
                    }
                Entry(group,ids,items,source,profile.attributes())
            }
            return SktjfElectiveSchedule(entries)
        }
    }
}

internal fun SktjfElectiveSlotResult.Managed.selectedLesson(): ResolvedLesson? {
    val id=(resolution as? SktjfElectiveResolution.Chosen)?.lessonId ?: return null
    val item=options.singleOrNull { it.sourceLessonId==id } ?: return null
    return ResolvedLesson(item.content.subject,item.content.teachers.joinToString("、"),
        item.content.rooms.joinToString("、").ifBlank { null },extraLessonId=item.id)
}
