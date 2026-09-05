package jp.linkserver.nittcsc.sync

import jp.linkserver.nittcsc.data.SchedulerRepository
import jp.linkserver.nittcsc.data.SyncRegisteredDeviceEntity
import jp.linkserver.nittcsc.data.requireCompatibleSyncProtocols
import org.json.JSONObject

internal class SyncPayloadCoordinator(
    private val nowProvider: () -> Long = System::currentTimeMillis
) {
    fun detectConflicts(
        localPayload: JSONObject,
        remotePayload: JSONObject,
        registeredDevice: SyncRegisteredDeviceEntity?,
        forceConflictOnDifference: Boolean
    ): List<SyncConflict> {
        requireCompatibleSyncProtocols(localPayload, remotePayload)
        val localDeviceName = localPayload.getJSONObject("device").optString("deviceName", "この端末")
        val remoteDeviceName = remotePayload.getJSONObject("device").optString("deviceName", "相手端末")
        val conflicts = mutableListOf<SyncConflict>()

        commonDatasets(localPayload, remotePayload).forEach { key ->
            val localContent = localPayload.opt(key)?.toString() ?: ""
            val remoteContent = remotePayload.opt(key)?.toString() ?: ""
            if (localContent == remoteContent) return@forEach

            val localUpdatedAt = metadataTimestamp(localPayload, key)
            val remoteUpdatedAt = metadataTimestamp(remotePayload, key)
            val bothChanged = hasSyncConflict(
                forceConflictOnDifference = forceConflictOnDifference,
                localUpdatedAt = localUpdatedAt,
                remoteUpdatedAt = remoteUpdatedAt,
                lastSyncedAt = registeredDevice.lastSyncedAt(key)
            )
            if (bothChanged) {
                conflicts += SyncConflict(
                    datasetKey = key,
                    label = datasetLabel(key),
                    localUpdatedAt = localUpdatedAt,
                    remoteUpdatedAt = remoteUpdatedAt,
                    localDeviceName = localDeviceName,
                    remoteDeviceName = remoteDeviceName
                )
            }
        }
        return conflicts
    }

    fun buildMergedPayload(
        localPayload: JSONObject,
        remotePayload: JSONObject,
        remoteDeviceId: String,
        resolutions: Map<String, SyncChoice>
    ): JSONObject {
        requireCompatibleSyncProtocols(localPayload, remotePayload)
        val merged = JSONObject(localPayload.toString())
        val metadata = JSONObject()
        val now = nowProvider()
        val localMeta = localPayload.getJSONObject("metadata")
        val remoteMeta = remotePayload.getJSONObject("metadata")
        val commonDatasetKeys = commonDatasets(localPayload, remotePayload).toSet()

        commonDatasetKeys.forEach { key ->
            val localUpdatedAt = metadataTimestamp(localPayload, key)
            val remoteUpdatedAt = metadataTimestamp(remotePayload, key)
            val choice = resolveSyncChoice(
                datasetKey = key,
                resolutions = resolutions,
                localUpdatedAt = localUpdatedAt,
                remoteUpdatedAt = remoteUpdatedAt
            )
            val sourcePayload = if (choice == SyncChoice.LOCAL || !remotePayload.has(key)) {
                localPayload
            } else {
                remotePayload
            }
            val sourceMeta = if (sourcePayload === localPayload) localMeta else remoteMeta
            merged.put(key, sourcePayload.get(key))
            metadata.put(
                key,
                JSONObject().also { item ->
                    val sourceUpdatedAt = sourceMeta.optJSONObject(key)?.optLong("updatedAt", 0L) ?: 0L
                    item.put("updatedAt", maxOf(now, sourceUpdatedAt))
                    item.put(
                        "updatedByDeviceId",
                        sourceMeta.optJSONObject(key)?.optString(
                            "updatedByDeviceId",
                            if (sourcePayload === localPayload) "" else remoteDeviceId
                        ) ?: ""
                    )
                }
            )
        }

        allSyncDatasets()
            .filter { it !in commonDatasetKeys && localPayload.has(it) }
            .forEach { key -> metadata.put(key, localMeta.optJSONObject(key) ?: JSONObject()) }

        merged.put("metadata", metadata)
        return merged
    }

    private fun metadataTimestamp(payload: JSONObject, key: String): Long {
        return payload.getJSONObject("metadata").optJSONObject(key)?.optLong("updatedAt", 0L) ?: 0L
    }

    private fun commonDatasets(localPayload: JSONObject, remotePayload: JSONObject): List<String> {
        return allSyncDatasets().filter { key -> localPayload.has(key) && remotePayload.has(key) }
    }

    private fun allSyncDatasets(): List<String> = SchedulerRepository.SYNC_DATASET_KEYS

    private fun datasetLabel(key: String): String = when (key) {
        SchedulerRepository.DATASET_TASKS -> "課題"
        SchedulerRepository.DATASET_PLANS -> "予定"
        SchedulerRepository.DATASET_LESSONS -> "時間割"
        SchedulerRepository.DATASET_DAY_TYPES -> "A/B表"
        SchedulerRepository.DATASET_LONG_BREAKS -> "長期休み"
        SchedulerRepository.DATASET_CANCELLED_LESSONS -> "休講情報"
        SchedulerRepository.DATASET_CHANGED_LESSONS -> "授業変更"
        SchedulerRepository.DATASET_LESSON_NOTES -> "授業メモ"
        SchedulerRepository.DATASET_EXAM_TIMETABLES -> "テスト時間割"
        else -> key
    }

    private fun SyncRegisteredDeviceEntity?.lastSyncedAt(key: String): Long {
        val device = this ?: return 0L
        return when (key) {
            SchedulerRepository.DATASET_TASKS -> device.lastTasksSyncAt
            SchedulerRepository.DATASET_PLANS -> device.lastPlansSyncAt
            SchedulerRepository.DATASET_LESSONS -> device.lastLessonsSyncAt
            SchedulerRepository.DATASET_DAY_TYPES -> device.lastDayTypesSyncAt
            SchedulerRepository.DATASET_LONG_BREAKS -> device.lastLongBreaksSyncAt
            SchedulerRepository.DATASET_CANCELLED_LESSONS -> device.lastCancelledLessonsSyncAt
            SchedulerRepository.DATASET_CHANGED_LESSONS -> device.lastChangedLessonsSyncAt
            SchedulerRepository.DATASET_LESSON_NOTES -> device.lastLessonNotesSyncAt
            SchedulerRepository.DATASET_EXAM_TIMETABLES -> device.lastExamTimetablesSyncAt
            else -> 0L
        }
    }
}

internal fun hasSyncConflict(
    forceConflictOnDifference: Boolean,
    localUpdatedAt: Long,
    remoteUpdatedAt: Long,
    lastSyncedAt: Long
): Boolean {
    return forceConflictOnDifference ||
        (localUpdatedAt > lastSyncedAt && remoteUpdatedAt > lastSyncedAt)
}

internal fun resolveSyncChoice(
    datasetKey: String,
    resolutions: Map<String, SyncChoice>,
    localUpdatedAt: Long,
    remoteUpdatedAt: Long
): SyncChoice {
    return resolutions[datasetKey]
        ?: if (datasetKey == SchedulerRepository.DATASET_CANCELLED_LESSONS) {
            resolutions[SchedulerRepository.DATASET_LESSONS]
        } else {
            null
        }
        ?: if (remoteUpdatedAt > localUpdatedAt) SyncChoice.REMOTE else SyncChoice.LOCAL
}
