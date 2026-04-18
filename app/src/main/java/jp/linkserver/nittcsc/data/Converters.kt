package jp.linkserver.nittcsc.data

import androidx.room.TypeConverter
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
    fun fromLessonMode(value: LessonMode): String = value.name

    @TypeConverter
    fun toLessonMode(value: String): LessonMode = LessonMode.valueOf(value)
}
