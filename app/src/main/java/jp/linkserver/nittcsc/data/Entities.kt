package jp.linkserver.nittcsc.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class DayType {
    A,
    B,
    HOLIDAY
}

enum class LessonMode {
    WEEKLY,
    ALTERNATING
}

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val termStart: LocalDate,
    val termEnd: LocalDate,
    val enableLocalAi: Boolean = false,
    val hfToken: String? = null,
    val periodsPerDay: Int = 4,
    val periodDurationMin: Int = 90,
    val breakBetweenPeriodsMin: Int = 10,
    val lunchBreakMin: Int = 60,
    val lunchAfterPeriod: Int = 2,
    val firstPeriodStartHour: Int = 8,
    val firstPeriodStartMinute: Int = 40,
    val useKosenMode: Boolean = true,
    val useDrawerNavigation: Boolean = false,
    val addTasksToCalendar: Boolean = false,
    val showCurrentTimeMarker: Boolean = false,
    val arrivalHour: Int = -1,
    val arrivalMinute: Int = -1,
    val departureHour: Int = -1,
    val departureMinute: Int = -1,
    val unifyTaskPlanView: Boolean = false
)

@Entity(tableName = "day_types")
data class DayTypeEntity(
    @PrimaryKey val date: LocalDate,
    val dayType: DayType
)

@Entity(tableName = "long_breaks")
data class LongBreakEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate
)

@Entity(
    tableName = "lessons",
    indices = [Index(value = ["dayOfWeek", "slotIndex"], unique = true)]
)
data class LessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int,
    val slotIndex: Int,
    val mode: LessonMode,
    val weeklySubject: String,
    val weeklyTeacher: String,
    val weeklyLocation: String? = null,
    val aSubject: String,
    val aTeacher: String,
    val aLocation: String? = null,
    val bSubject: String,
    val bTeacher: String,
    val bLocation: String? = null
)

data class LessonDraft(
    val mode: LessonMode = LessonMode.WEEKLY,
    val weeklySubject: String = "",
    val weeklyTeacher: String = "",
    val weeklyLocation: String = "",
    val aSubject: String = "",
    val aTeacher: String = "",
    val aLocation: String = "",
    val bSubject: String = "",
    val bTeacher: String = "",
    val bLocation: String = ""
)

data class ResolvedLesson(
    val subject: String,
    val teacher: String,
    val location: String? = null
)

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["dueDate"])]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long? = null,
    val subject: String,
    val teacher: String? = null,
    val title: String,
    val description: String? = null,
    val dueDate: LocalDate,
    val dueHour: Int = 23,
    val dueMinute: Int = 59,
    val isCompleted: Boolean = false,
    val completedDate: LocalDate? = null,
    val createdDate: LocalDate,
    val priority: Int = 0, // 0=通常, 1=重要, -1=低
    val useTeacherMatching: Boolean = false,
    val calendarEventId: Long? = null
)

@Entity(
    tableName = "plans",
    indices = [Index(value = ["dueDate"])]
)
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long? = null,
    val subject: String,
    val teacher: String? = null,
    val title: String,
    val description: String? = null,
    val dueDate: LocalDate,
    val dueHour: Int = 23,
    val dueMinute: Int = 59,
    val isCompleted: Boolean = false,
    val completedDate: LocalDate? = null,
    val createdDate: LocalDate,
    val priority: Int = 0,
    val useTeacherMatching: Boolean = false,
    val calendarEventId: Long? = null
)
