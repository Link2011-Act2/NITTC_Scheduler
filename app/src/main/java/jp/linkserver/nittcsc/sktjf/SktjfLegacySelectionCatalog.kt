package jp.linkserver.nittcsc.sktjf

import java.security.MessageDigest

/**
 * The first 2026 Beta releases predate explicit selection groups. These entries
 * are keyed by the hash of the complete published asset, not by a coincidental
 * subject name or an overlap. The 4B/4E alternatives were identified by the
 * timetable reviewer. For 5E, the 2025 fourth-year curriculum model groups the
 * two subjects as alternatives; the same pair is in the 2026 timetable.
 * https://www.tsuruoka-nct.ac.jp/wp-content/uploads/2025/09/r7_matome2.pdf
 */
internal object SktjfLegacySelectionCatalog {
    private data class Entry(val digest: String, val year: Int, val term: String, val revision: String,
        val classId: String, val day: Int, val period: Int, val cycle: SktjfCycle, val groupId: String, val ids: List<String>)
    private val entries = listOf(
        Entry("41ae6d38e9090763009157c8c6a2afeb4f3a200564475079378d3d91161d2ed0", 2026, "S", "2026.S.2.Beta", "4B", 4, 4,
            SktjfCycle.WEEKLY, "legacy-2026-s-4b-4-4", listOf("068d9dce-7406-447f-91ea-2fce093fe4c9", "a3d2250b-eb1b-4d93-968d-21554ec9d28e")),
        Entry("dd6a1f5c2e99e31baf0fea5a6152899cb807e7ad7fcacb7a1ee148e13c22d14c", 2026, "S", "2026.S.2.Beta", "4E", 4, 3,
            SktjfCycle.WEEKLY, "legacy-2026-s-4e-4-3", listOf("46204cd0-c13e-4cf3-aaec-663a60e2f2af", "f262cbe0-f2c4-4eaa-b91b-7c66e384de06")),
        Entry("d4c81f50c39b728d16cb73e5b25aed0cf852fa4d1a775b90fa2da9183383baf5", 2026, "F", "2026.F.1.Beta", "5E", 1, 1,
            SktjfCycle.A, "legacy-2026-f-5e-1-1-a", listOf("28189e85-f0d9-47fa-b69f-d298761a7eb2", "5bc017f2-3ac9-4072-a1a3-114b4fc3938e"))
    )

    fun adapt(bytes: ByteArray, candidate: SktjfV2Candidate): SktjfV3Candidate? {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return adaptVerifiedHash(digest, candidate)
    }

    internal fun adaptVerifiedHash(digest: String, candidate: SktjfV2Candidate): SktjfV3Candidate? {
        val document = candidate.document
        val entry = entries.singleOrNull { it.digest == digest && it.year == document.academicYear && it.term == document.term &&
            it.revision == document.revision && it.classId == document.classId } ?: return null
        val members = entry.ids.map { id -> document.lessons.singleOrNull { it.id == id } ?: return null }
        if (members.any { it.dayOfWeek != entry.day || it.period != entry.period || it.cycle != entry.cycle || it.sources.isEmpty() }) return null
        val group = SktjfSelectionGroup(entry.groupId, entry.day, entry.period, entry.ids, members.flatMap { it.sources }.distinct())
        return SktjfV3Candidate(candidate, listOf(group))
    }
}
