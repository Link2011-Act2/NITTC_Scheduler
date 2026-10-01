package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.LessonDraft
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import java.time.LocalDate
import java.util.Locale

data class LessonAutocompleteOptions(
    val subjectSuggestions: List<String>,
    val subjectTeacherCandidates: Map<String, List<String>>,
    val subjectTeacherLocationCandidates: Map<Pair<String, String>, List<String>>,
    val subjectLocationCandidates: Map<String, List<String>>,
    private val currentTermSubjects: Set<String>
) {
    val naturalLanguageCandidates: List<NaturalLanguageLessonCandidate>
        get() = subjectSuggestions.flatMap { subject ->
            subjectTeacherCandidates[subject].orEmpty().ifEmpty { listOf("") }.map { teacher ->
                NaturalLanguageLessonCandidate(subject, teacher.takeIf { it.isNotBlank() })
            }
        }

    fun resolveTeacherForAutoFill(subject: String, suggestedTeacher: String?): String? {
        val key = subjectSuggestions.firstOrNull { it.equals(subject.trim(), ignoreCase = true) }
        val teachers = subjectTeacherCandidates[key].orEmpty()
        val suggested = suggestedTeacher?.trim()?.takeIf { it.isNotBlank() }
        // 現学期に教科がなければ、選択した授業や読み取り結果の教員も利用できる。
        if (normalizeSubject(subject) !in currentTermSubjects) {
            return suggested ?: teachers.singleOrNull()
        }
        // 教員未登録や候補が複数の場合は、他学期の教員で埋めない。
        return teachers.singleOrNull()
            ?: teachers.firstOrNull { it.equals(suggested, ignoreCase = true) }
    }

    fun resolveDraftTeachersForAutoFill(draft: LessonDraft): LessonDraft = draft.copy(
        weeklyTeacher = resolveTeacherForAutoFill(draft.weeklySubject, draft.weeklyTeacher).orEmpty(),
        aTeacher = resolveTeacherForAutoFill(draft.aSubject, draft.aTeacher).orEmpty(),
        bTeacher = resolveTeacherForAutoFill(draft.bSubject, draft.bTeacher).orEmpty()
    )
}

private data class LessonAutocompleteEntry(
    val subject: String,
    val teacher: String,
    val location: String?
)

data class TeacherAutoFillResult(val teacher: String, val autoFilledTeacher: String?)

fun updateTeacherAutoFill(
    teacher: String,
    autoFilledTeacher: String?,
    candidates: List<String>
): TeacherAutoFillResult {
    if (autoFilledTeacher == null && teacher.isNotBlank()) {
        return TeacherAutoFillResult(teacher, null)
    }
    candidates.singleOrNull()?.let { return TeacherAutoFillResult(it, it) }
    // 教科や学期の変更で候補から外れた自動入力だけを消し、手入力は維持する。
    if (teacher == autoFilledTeacher && teacher !in candidates) {
        return TeacherAutoFillResult("", null)
    }
    return TeacherAutoFillResult(teacher, autoFilledTeacher)
}

fun buildLessonAutocompleteOptions(
    lessons: Collection<LessonEntity>,
    currentDate: LocalDate,
    semesterTimetablesEnabled: Boolean,
    secondTermStartMonth: Int = 10,
    secondTermStartDay: Int = 1
): LessonAutocompleteOptions {
    val academicYear = academicYearForDate(currentDate)
    val timetableTerm = timetableTermForDate(
        currentDate, semesterTimetablesEnabled, secondTermStartMonth, secondTermStartDay
    )
    val entriesByLesson = lessons.map { lesson -> lesson to lesson.autocompleteEntries() }
    fun isCurrent(lesson: LessonEntity) =
        lesson.academicYear == academicYear && lesson.timetableTerm == timetableTerm
    val currentSubjects = entriesByLesson
        .filter { (lesson, _) -> isCurrent(lesson) }
        .flatMap { (_, entries) -> entries.map { normalizeSubject(it.subject) } }
        .toSet()
    val entriesBySubject = entriesByLesson
        .flatMap { (lesson, entries) ->
            entries.filter { isCurrent(lesson) || normalizeSubject(it.subject) !in currentSubjects }
        }
        .distinct()
        .groupBy { normalizeSubject(it.subject) }
    val subjectEntries = entriesBySubject.values.associateBy { it.first().subject }

    return LessonAutocompleteOptions(
        subjectSuggestions = subjectEntries.keys.sorted(),
        subjectTeacherCandidates = subjectEntries.mapValues { (_, entries) ->
            entries.map { it.teacher }.filter { it.isNotBlank() }.distinct().sorted()
        },
        subjectTeacherLocationCandidates = subjectEntries.flatMap { (subject, entries) ->
            entries.filter { it.teacher.isNotBlank() && it.location != null }
                .map { (subject to it.teacher) to checkNotNull(it.location) }
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, locations) -> locations.distinct().sorted() },
        subjectLocationCandidates = subjectEntries.mapValues { (_, entries) ->
            entries.mapNotNull { it.location }.distinct().sorted()
        },
        currentTermSubjects = currentSubjects
    )
}

private fun normalizeSubject(subject: String): String = subject.trim().lowercase(Locale.ROOT)

private fun LessonEntity.autocompleteEntries(): List<LessonAutocompleteEntry> = when (mode) {
    LessonMode.WEEKLY -> listOf(LessonAutocompleteEntry(weeklySubject, weeklyTeacher, weeklyLocation))
    LessonMode.ALTERNATING -> listOf(
        LessonAutocompleteEntry(aSubject, aTeacher, aLocation),
        LessonAutocompleteEntry(bSubject, bTeacher, bLocation)
    )
}.map { entry ->
    entry.copy(
        subject = entry.subject.trim(), teacher = entry.teacher.trim(),
        location = entry.location?.trim()?.takeIf { it.isNotEmpty() }
    )
}.filter { it.subject.isNotBlank() }
