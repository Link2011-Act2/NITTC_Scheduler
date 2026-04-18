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
}
