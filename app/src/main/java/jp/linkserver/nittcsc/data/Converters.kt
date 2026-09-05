package jp.linkserver.nittcsc.data

import androidx.room.TypeConverter
import jp.linkserver.nittcsc.logic.PeriodLabelStyle
import jp.linkserver.nittcsc.logic.TimetableTerm
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromDayType(value: DayType): String = value.name

    @TypeConverter
    fun toDayType(value: String): DayType = DayType.valueOf(value)

    @TypeConverter
    fun fromNullableDayType(value: DayType?): String? = value?.name

    @TypeConverter
    fun toNullableDayType(value: String?): DayType? = value?.let(DayType::valueOf)

    @TypeConverter
    fun fromLessonMode(value: LessonMode): String = value.name

    @TypeConverter
    fun toLessonMode(value: String): LessonMode = LessonMode.valueOf(value)

    @TypeConverter
    fun fromTimetableTerm(value: TimetableTerm): String = value.name

    @TypeConverter
    fun toTimetableTerm(value: String): TimetableTerm =
        runCatching { TimetableTerm.valueOf(value) }.getOrDefault(TimetableTerm.FIRST)

    @TypeConverter
    fun fromLessonStartNotificationChipMode(value: LessonStartNotificationChipMode): String = value.name

    @TypeConverter
    fun toLessonStartNotificationChipMode(value: String): LessonStartNotificationChipMode =
        runCatching { LessonStartNotificationChipMode.valueOf(value) }
            .getOrDefault(LessonStartNotificationChipMode.MINUTE_TEXT)

    @TypeConverter
    fun fromPeriodLabelStyle(value: PeriodLabelStyle): String = value.name

    @TypeConverter
    fun toPeriodLabelStyle(value: String): PeriodLabelStyle =
        runCatching { PeriodLabelStyle.valueOf(value) }
            .getOrDefault(PeriodLabelStyle.PAIR_KOSHI)
}
