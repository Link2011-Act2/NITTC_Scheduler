package jp.linkserver.nittcsc.sktjf

/** Eligibility (profile, A/B and official opportunity) is resolved before this choice. */
internal sealed interface SktjfElectiveResolution {
    data object Empty : SktjfElectiveResolution
    data class Chosen(val lessonId: String) : SktjfElectiveResolution
    data class Undecided(val lessonIds: List<String>) : SktjfElectiveResolution
    data class NeedsReview(val previousLessonId: String, val lessonIds: List<String>) : SktjfElectiveResolution
}

/** Only explicit groups call this function; unrelated simultaneous lessons are never inferred as electives. */
internal fun resolveSktjfElective(allLessonIds: List<String>, eligibleLessonIds: List<String>, selectedLessonId: String?): SktjfElectiveResolution {
    require(allLessonIds.none { it.isBlank() } && allLessonIds.distinct().size == allLessonIds.size &&
        eligibleLessonIds.distinct().size == eligibleLessonIds.size && allLessonIds.containsAll(eligibleLessonIds))
    if (selectedLessonId != null && selectedLessonId !in allLessonIds)
        return SktjfElectiveResolution.NeedsReview(selectedLessonId, eligibleLessonIds)
    if (selectedLessonId != null) return if (selectedLessonId in eligibleLessonIds)
        SktjfElectiveResolution.Chosen(selectedLessonId) else SktjfElectiveResolution.Empty
    return when (eligibleLessonIds.size) {
        0 -> SktjfElectiveResolution.Empty
        1 -> SktjfElectiveResolution.Chosen(eligibleLessonIds.single())
        else -> SktjfElectiveResolution.Undecided(eligibleLessonIds.toList())
    }
}
