package jp.linkserver.nittcsc.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SchedulerDao {

    @Query("SELECT * FROM settings WHERE id = 1")
    fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettings(): SettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: SettingsEntity)

    @Query("SELECT * FROM day_types ORDER BY date")
    fun observeDayTypes(): Flow<List<DayTypeEntity>>

    @Query("SELECT * FROM day_types")
    suspend fun getDayTypesOnce(): List<DayTypeEntity>

    @Query("SELECT * FROM day_types WHERE date = :date")
    suspend fun getDayType(date: LocalDate): DayTypeEntity?

    @Upsert
    suspend fun upsertDayType(dayType: DayTypeEntity)

    @Upsert
    suspend fun upsertDayTypes(dayTypes: List<DayTypeEntity>)

    @Query("DELETE FROM day_types")
    suspend fun deleteAllDayTypes()

    @Query("DELETE FROM day_types WHERE date < :startDate OR date > :endDate")
    suspend fun deleteDayTypesOutsideRange(startDate: LocalDate, endDate: LocalDate)

    @Query("SELECT * FROM cancelled_lessons ORDER BY date, slotIndex")
    fun observeCancelledLessons(): Flow<List<CancelledLessonEntity>>

    @Query("SELECT * FROM cancelled_lessons ORDER BY date, slotIndex")
    suspend fun getCancelledLessonsOnce(): List<CancelledLessonEntity>

    @Query("SELECT * FROM cancelled_lessons WHERE date = :date AND slotIndex = :slotIndex LIMIT 1")
    suspend fun getCancelledLesson(date: LocalDate, slotIndex: Int): CancelledLessonEntity?

    @Upsert
    suspend fun upsertCancelledLesson(cancelledLesson: CancelledLessonEntity)

    @Query("DELETE FROM cancelled_lessons WHERE date = :date AND slotIndex = :slotIndex")
    suspend fun deleteCancelledLesson(date: LocalDate, slotIndex: Int)

    @Query("DELETE FROM cancelled_lessons")
    suspend fun deleteAllCancelledLessons()

    @Query("SELECT * FROM long_breaks ORDER BY startDate")
    fun observeLongBreaks(): Flow<List<LongBreakEntity>>

    @Query("SELECT * FROM long_breaks ORDER BY startDate")
    suspend fun getLongBreaksOnce(): List<LongBreakEntity>

    @Upsert
    suspend fun upsertLongBreak(longBreak: LongBreakEntity)

    @Delete
    suspend fun deleteLongBreak(longBreak: LongBreakEntity)

    @Query("SELECT * FROM lessons ORDER BY dayOfWeek, slotIndex")
    fun observeLessons(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons ORDER BY dayOfWeek, slotIndex")
    suspend fun getLessonsOnce(): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE dayOfWeek = :dayOfWeek AND slotIndex = :slotIndex LIMIT 1")
    suspend fun getLesson(dayOfWeek: Int, slotIndex: Int): LessonEntity?

    @Upsert
    suspend fun upsertLesson(lesson: LessonEntity)

    @Query("DELETE FROM lessons")
    suspend fun deleteAllLessons()

    @Query("SELECT * FROM tasks ORDER BY dueDate, dueHour, dueMinute, priority DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY dueDate, dueHour, dueMinute, priority DESC")
    suspend fun getTasksOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE dueDate = :date ORDER BY dueHour, dueMinute, priority DESC")
    suspend fun getTasksByDate(date: LocalDate): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE dueDate >= :fromDate AND dueDate <= :toDate ORDER BY dueDate, dueHour, dueMinute, priority DESC")
    suspend fun getTasksInRange(fromDate: LocalDate, toDate: LocalDate): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY dueDate, dueHour, dueMinute, priority DESC")
    fun observeIncompleteTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY dueDate, dueHour, dueMinute, priority DESC")
    suspend fun getIncompleteTasksOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE lessonId = :lessonId ORDER BY dueDate, dueHour, dueMinute, priority DESC")
    suspend fun getTasksByLessonId(lessonId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Upsert
    suspend fun upsertTask(task: TaskEntity)

    @Upsert
    suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM tasks WHERE lessonId = :lessonId")
    suspend fun deleteTasksByLessonId(lessonId: Long)

    @Query("DELETE FROM long_breaks")
    suspend fun deleteAllLongBreaks()

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("SELECT * FROM plans ORDER BY dueDate, dueHour, dueMinute, priority DESC")
    fun observePlans(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans ORDER BY dueDate, dueHour, dueMinute, priority DESC")
    suspend fun getPlansOnce(): List<PlanEntity>

    @Query("SELECT * FROM plans WHERE dueDate = :date ORDER BY dueHour, dueMinute, priority DESC")
    suspend fun getPlansByDate(date: LocalDate): List<PlanEntity>

    @Query("SELECT * FROM plans WHERE dueDate >= :fromDate AND dueDate <= :toDate ORDER BY dueDate, dueHour, dueMinute, priority DESC")
    suspend fun getPlansInRange(fromDate: LocalDate, toDate: LocalDate): List<PlanEntity>

    @Query("SELECT * FROM plans WHERE isCompleted = 0 ORDER BY dueDate, dueHour, dueMinute, priority DESC")
    fun observeIncompletePlans(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans WHERE isCompleted = 0 ORDER BY dueDate, dueHour, dueMinute, priority DESC")
    suspend fun getIncompletePlansOnce(): List<PlanEntity>

    @Query("SELECT * FROM plans WHERE lessonId = :lessonId ORDER BY dueDate, dueHour, dueMinute, priority DESC")
    suspend fun getPlansByLessonId(lessonId: Long): List<PlanEntity>

    @Query("SELECT * FROM plans WHERE id = :id LIMIT 1")
    suspend fun getPlanById(id: Long): PlanEntity?

    @Upsert
    suspend fun upsertPlan(plan: PlanEntity)

    @Upsert
    suspend fun upsertPlans(plans: List<PlanEntity>)

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun deletePlan(id: Long)

    @Query("DELETE FROM plans WHERE lessonId = :lessonId")
    suspend fun deletePlansByLessonId(lessonId: Long)

    @Query("DELETE FROM plans")
    suspend fun deleteAllPlans()

    @Query("SELECT * FROM sync_profile WHERE id = 1")
    fun observeSyncProfile(): Flow<SyncProfileEntity?>

    @Query("SELECT * FROM sync_profile WHERE id = 1")
    suspend fun getSyncProfile(): SyncProfileEntity?

    @Upsert
    suspend fun upsertSyncProfile(profile: SyncProfileEntity)

    @Query("SELECT * FROM sync_dataset_meta")
    suspend fun getAllSyncDatasetMeta(): List<SyncDatasetMetaEntity>

    @Upsert
    suspend fun upsertSyncDatasetMeta(meta: SyncDatasetMetaEntity)

    @Upsert
    suspend fun upsertSyncDatasetMetaList(meta: List<SyncDatasetMetaEntity>)

    @Query("SELECT * FROM sync_registered_devices ORDER BY deviceName")
    fun observeSyncRegisteredDevices(): Flow<List<SyncRegisteredDeviceEntity>>

    @Query("SELECT * FROM sync_registered_devices ORDER BY deviceName")
    suspend fun getSyncRegisteredDevices(): List<SyncRegisteredDeviceEntity>

    @Query("SELECT * FROM sync_registered_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getSyncRegisteredDevice(deviceId: String): SyncRegisteredDeviceEntity?

    @Upsert
    suspend fun upsertSyncRegisteredDevice(device: SyncRegisteredDeviceEntity)

    @Query("DELETE FROM sync_registered_devices WHERE deviceId = :deviceId")
    suspend fun deleteSyncRegisteredDevice(deviceId: String)

    @Query("SELECT * FROM sync_trusted_peers WHERE trustToken = :token LIMIT 1")
    suspend fun getSyncTrustedPeerByToken(token: String): SyncTrustedPeerEntity?

    @Upsert
    suspend fun upsertSyncTrustedPeer(peer: SyncTrustedPeerEntity)
}
