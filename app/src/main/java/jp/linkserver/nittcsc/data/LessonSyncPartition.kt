package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.logic.TimetableTerm
import org.json.JSONObject

internal data class LessonSyncPartition(val academicYear: Int, val term: TimetableTerm) {
    val datasetKey: String get() = "lessons/$academicYear/${term.name}"
}

internal fun lessonSyncPartition(key: String): LessonSyncPartition? {
    val parts = key.split('/')
    if (parts.size != 3 || parts[0] != SchedulerRepository.DATASET_LESSONS) return null
    val year = parts[1].toIntOrNull()?.takeIf { it in 1900..9999 } ?: return null
    val term = TimetableTerm.entries.firstOrNull { it.name == parts[2] } ?: return null
    return LessonSyncPartition(year, term)
}

internal fun JSONObject.lessonPartitionKeys(): Set<String> = keys().asSequence()
    .filter { lessonSyncPartition(it) != null }
    .toSet()

internal fun syncDatasetKeys(local: JSONObject, remote: JSONObject? = null): List<String> =
    SchedulerRepository.SYNC_DATASET_KEYS.filter { it != SchedulerRepository.DATASET_LESSONS } +
        (local.lessonPartitionKeys() + remote?.lessonPartitionKeys().orEmpty()).sorted()

internal const val SECOND_TERM_START_SYNC_KEY = "secondTermStart"

internal fun requireMatchingSecondTermStart(local: JSONObject, remote: JSONObject) {
    require(local.optString(SECOND_TERM_START_SYNC_KEY) == remote.optString(SECOND_TERM_START_SYNC_KEY)) {
        "後期開始日の設定が端末間で異なります。両方の端末で同じ日付を設定してください。"
    }
}
