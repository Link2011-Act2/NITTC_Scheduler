package jp.linkserver.nittcsc.logic

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class NaturalLanguageLessonCandidate(
    val subject: String,
    val teacher: String?
)

data class NaturalLanguageTaskParseResult(
    val subject: String,
    val teacher: String?,
    val title: String,
    val description: String?,
    val dueDate: LocalDate,
    val dueHour: Int,
    val dueMinute: Int
)

object NaturalLanguageTaskParser {
    private val explicitDateRegex = Regex("""(\d{4})[/-](\d{1,2})[/-](\d{1,2})""")
    private val monthDayRegex = Regex("""(\d{1,2})\s*(?:月|/)\s*(\d{1,2})\s*日?""")
    private val daysAfterRegex = Regex("""(\d{1,2})\s*日後""")
    private val timeRegex = Regex("""(?:(午前|午後)\s*)?(\d{1,2})(?:(?:[:：](\d{1,2}))|(?:時\s*(\d{1,2})?\s*分?))""")
    private val weekdayRegex = Regex("""(今週|来週)?\s*([月火水木金土日])曜?日?""")

    fun parse(
        input: String,
        candidates: List<NaturalLanguageLessonCandidate>,
        today: LocalDate,
        now: LocalTime
    ): NaturalLanguageTaskParseResult? {
        val raw = input.trim()
        if (raw.isBlank()) return null

        val matchedLesson = findBestLesson(raw, candidates)
        val dueDate = parseDate(raw, today) ?: today
        val (dueHour, dueMinute) = parseTime(raw) ?: (23 to 59)
        val title = buildTitle(raw, matchedLesson, dueDate, dueHour, dueMinute).ifBlank {
            if (matchedLesson?.subject.isNullOrBlank()) "課題" else "${matchedLesson.subject}の課題"
        }
        val description = buildDescription(raw, today, now)

        return NaturalLanguageTaskParseResult(
            subject = matchedLesson?.subject.orEmpty(),
            teacher = matchedLesson?.teacher?.takeIf { it.isNotBlank() },
            title = title,
            description = description,
            dueDate = dueDate,
            dueHour = dueHour,
            dueMinute = dueMinute
        )
    }

    private fun parseDate(input: String, today: LocalDate): LocalDate? {
        explicitDateRegex.find(input)?.let { match ->
            return runCatching {
                LocalDate.of(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt()
                )
            }.getOrNull()
        }

        monthDayRegex.find(input)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            return runCatching {
                var date = LocalDate.of(today.year, month, day)
                if (date.isBefore(today)) date = date.plusYears(1)
                date
            }.getOrNull()
        }

        daysAfterRegex.find(input)?.let { match ->
            return today.plusDays(match.groupValues[1].toLong())
        }

        return when {
            input.contains("明々後日") || input.contains("しあさって") -> today.plusDays(3)
            input.contains("明後日") || input.contains("あさって") -> today.plusDays(2)
            input.contains("明日") || input.contains("あした") -> today.plusDays(1)
            input.contains("今日") || input.contains("本日") -> today
            else -> parseWeekday(input, today)
        }
    }

    private fun parseWeekday(input: String, today: LocalDate): LocalDate? {
        val match = weekdayRegex.find(input) ?: return null
        val target = when (match.groupValues[2]) {
            "月" -> DayOfWeek.MONDAY
            "火" -> DayOfWeek.TUESDAY
            "水" -> DayOfWeek.WEDNESDAY
            "木" -> DayOfWeek.THURSDAY
            "金" -> DayOfWeek.FRIDAY
            "土" -> DayOfWeek.SATURDAY
            "日" -> DayOfWeek.SUNDAY
            else -> return null
        }
        val base = if (match.groupValues[1] == "来週") today.plusWeeks(1) else today
        val diff = (target.value - base.dayOfWeek.value + 7) % 7
        val daysToAdd = if (diff == 0 && match.groupValues[1].isBlank()) 7 else diff
        return base.plusDays(daysToAdd.toLong())
    }

    private fun parseTime(input: String): Pair<Int, Int>? {
        val match = timeRegex.find(input) ?: return null
        val amPm = match.groupValues[1]
        var hour = match.groupValues[2].toIntOrNull() ?: return null
        val minute = (match.groupValues[3].ifBlank { match.groupValues[4] }).toIntOrNull() ?: 0
        if (amPm == "午後" && hour in 1..11) hour += 12
        if (amPm == "午前" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun findBestLesson(
        input: String,
        candidates: List<NaturalLanguageLessonCandidate>
    ): NaturalLanguageLessonCandidate? {
        val normalizedInput = normalize(input)
        return candidates
            .filter { it.subject.isNotBlank() }
            .distinctBy { normalize(it.subject) to normalize(it.teacher.orEmpty()) }
            .map { candidate -> candidate to scoreLesson(normalizedInput, candidate) }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun scoreLesson(input: String, candidate: NaturalLanguageLessonCandidate): Int {
        val subject = normalize(candidate.subject)
        val compactSubject = compactSubject(subject)
        val teacher = normalize(candidate.teacher.orEmpty())
        val teacherSurname = teacher
            .split(",", "、", "，", " ", "　")
            .firstOrNull()
            .orEmpty()
        var score = 0
        if (subject.isNotBlank() && input.contains(subject)) score += 120
        if (compactSubject.isNotBlank() && compactSubject != subject && input.contains(compactSubject)) score += 95
        if (subject.isNotBlank() && subject.length >= 2 && input.contains(subject.take(2))) score += 45
        if (teacher.isNotBlank() && input.contains(teacher)) score += 100
        if (teacherSurname.length >= 2 && input.contains(teacherSurname)) score += 85
        return score
    }

    private fun buildTitle(
        input: String,
        matchedLesson: NaturalLanguageLessonCandidate?,
        dueDate: LocalDate,
        dueHour: Int,
        dueMinute: Int
    ): String {
        var title = input
            .replace("まで", "")
            .replace("締切", "")
            .replace("期限", "")
            .replace(explicitDateRegex, "")
            .replace(monthDayRegex, "")
            .replace(daysAfterRegex, "")
            .replace(timeRegex, "")
            .replace(weekdayRegex, "")
            .trim(' ', '　', ',', '，', '、', '。')
        val subject = matchedLesson?.subject.orEmpty()
        if (subject.isNotBlank()) {
            title = title
                .replace(subject, "")
                .replace(compactSubject(normalize(subject)), "")
                .trim(' ', '　', ',', '，', '、', '。')
        }
        val dueText = "%04d/%02d/%02d %02d:%02d".format(
            dueDate.year,
            dueDate.monthValue,
            dueDate.dayOfMonth,
            dueHour,
            dueMinute
        )
        return title.ifBlank { "課題" }.take(80).ifBlank { dueText }
    }

    private fun buildDescription(input: String, today: LocalDate, now: LocalTime): String {
        val hour = now.hour.toString().padStart(2, '0')
        val minute = now.minute.toString().padStart(2, '0')
        return "自然文から作成: $input\n解析基準: $today $hour:$minute"
    }

    private fun compactSubject(value: String): String {
        return value
            .replace("語", "")
            .replace("学", "")
            .replace("論", "")
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase()
            .replace(Regex("""[\s　]+"""), "")
            .replace("先生", "")
            .replace("教授", "")
    }
}
