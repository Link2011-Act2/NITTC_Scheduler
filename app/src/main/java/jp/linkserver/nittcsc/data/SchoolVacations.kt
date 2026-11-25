package jp.linkserver.nittcsc.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

data class SchoolVacationPeriod(
    val name: String,
    val start: LocalDate,
    val end: LocalDate?,
    val confirmedThrough: LocalDate,
    val estimatedEndMonth: Int?
)

@Entity(tableName = "school_vacations")
data class SchoolVacationEntity(
    @PrimaryKey val id: String,
    val revision: String,
    val sources: String,
    @Embedded(prefix = "base_") val base: SchoolVacationPeriod,
    @Embedded(prefix = "local_") val local: SchoolVacationPeriod?
)

// Keep the underlying calendar rows intact so shortening a vacation restores A/B information.
private fun vacationDay(date: LocalDate, existing: DayTypeEntity?): DayTypeEntity {
    if (existing?.overrideLessonDayOfWeek != null || existing?.holidaySpecialLabel != null) return existing
    return existing?.copy(dayType = DayType.HOLIDAY) ?: DayTypeEntity(date, DayType.HOLIDAY)
}

internal fun List<DayTypeEntity>.withSchoolVacations(vacations: List<SchoolVacationEntity>): List<DayTypeEntity> {
    val periods = vacations.mapNotNull { it.local }
    if (periods.isEmpty()) return this
    val days = associateByTo(mutableMapOf()) { it.date }
    for (period in periods) {
        var date = period.start
        while (date <= period.confirmedThrough) {
            days[date] = vacationDay(date, days[date])
            date = date.plusDays(1)
        }
    }
    return days.values.sortedBy { it.date }
}

internal suspend fun SchedulerDao.getEffectiveDayTypes(): List<DayTypeEntity> {
    val days = getDayTypesOnce()
    return if (getUsageMode()?.mode == SchedulerUsageMode.NITTC.name) days.withSchoolVacations(getSchoolVacations()) else days
}

internal suspend fun SchedulerDao.getEffectiveDayType(date: LocalDate): DayTypeEntity? {
    val day = getDayType(date)
    return if (getUsageMode()?.mode == SchedulerUsageMode.NITTC.name && getSchoolVacationsOn(date).isNotEmpty())
        vacationDay(date, day) else day
}
