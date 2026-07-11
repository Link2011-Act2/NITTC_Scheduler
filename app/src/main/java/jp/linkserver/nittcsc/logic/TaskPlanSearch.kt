package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.PlanEntity
import jp.linkserver.nittcsc.data.TaskEntity
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val searchDateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
private val searchSeparators = Regex("""[\s　,，、・/／\-ー_:.：]+""")
private val querySeparators = Regex("""[\s　]+""")

fun tokenizeSearchQuery(query: String): List<String> {
    return query
        .trim()
        .split(querySeparators)
        .map(::normalizeSearchText)
        .filter { it.isNotBlank() }
}

fun normalizeSearchText(value: String): String {
    return Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase()
        .replace(searchSeparators, "")
}

fun japaneseDayOfWeekSearchText(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "月曜日 月曜 月"
    DayOfWeek.TUESDAY -> "火曜日 火曜 火"
    DayOfWeek.WEDNESDAY -> "水曜日 水曜 水"
    DayOfWeek.THURSDAY -> "木曜日 木曜 木"
    DayOfWeek.FRIDAY -> "金曜日 金曜 金"
    DayOfWeek.SATURDAY -> "土曜日 土曜 土"
    DayOfWeek.SUNDAY -> "日曜日 日曜 日"
}

fun TaskEntity.matchesTaskPlanSearch(tokens: List<String>): Boolean {
    return taskPlanSearchText(
        type = "課題",
        title = title,
        subject = subject,
        teacher = teacher,
        description = description,
        dueDate = dueDate,
        hour = dueHour,
        minute = dueMinute
    ).let { searchableText -> tokens.all(searchableText::contains) }
}

fun PlanEntity.matchesTaskPlanSearch(tokens: List<String>): Boolean {
    return taskPlanSearchText(
        type = "予定",
        title = title,
        subject = subject,
        teacher = teacher,
        description = description,
        dueDate = dueDate,
        hour = dueHour,
        minute = dueMinute
    ).let { searchableText -> tokens.all(searchableText::contains) }
}

private fun taskPlanSearchText(
    type: String,
    title: String,
    subject: String,
    teacher: String?,
    description: String?,
    dueDate: LocalDate,
    hour: Int,
    minute: Int
): String {
    val timeText = "%02d:%02d %d時%d分".format(hour, minute, hour, minute)
    return normalizeSearchText(
        listOf(
            type,
            title,
            subject,
            teacher.orEmpty(),
            description.orEmpty(),
            dueDate.toString(),
            dueDate.format(searchDateFormatter),
            japaneseDayOfWeekSearchText(dueDate.dayOfWeek),
            timeText
        ).joinToString(" ")
    )
}
