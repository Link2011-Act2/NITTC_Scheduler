package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.logic.UpcomingLessonPhase
import jp.linkserver.nittcsc.logic.UpcomingLessonSelection
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Composable
internal fun TimetableDayPage(
    date: LocalDate,
    now: LocalDateTime?,
    selection: UpcomingLessonSelection<ResolvedLesson>?,
    showUpcoming: Boolean,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showUpcoming && now != null && date == now.toLocalDate() &&
            selection?.candidate?.date == date) {
            ExpressiveUpcomingLessonHero(checkNotNull(selection), now)
        }
        content()
    }
}

/** 今日のPagerページと一緒に移動する。固定高さを設けず、文字拡大時は縦に伸びる。 */
@Composable
internal fun ExpressiveUpcomingLessonHero(
    selection: UpcomingLessonSelection<ResolvedLesson>,
    now: LocalDateTime
) {
    val candidate = selection.candidate
    val ongoing = selection.phase == UpcomingLessonPhase.ONGOING
    val colors = MaterialTheme.colorScheme
    val minutes = ceil(Duration.between(now, if (ongoing) candidate.endsAt else candidate.startsAt)
        .seconds / 60.0).toLong().coerceAtLeast(1)
    val duration = if (minutes >= 60) stringResource(R.string.m3e_duration_hours_minutes, minutes / 60, minutes % 60)
        else stringResource(R.string.m3e_duration_minutes, minutes)
    val timeFormatter = remember { DateTimeFormatter.ofPattern("H:mm") }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("upcoming-lesson-card"),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 24.dp),
        color = if (ongoing) colors.tertiaryContainer else colors.primaryContainer,
        contentColor = if (ongoing) colors.onTertiaryContainer else colors.onPrimaryContainer
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (ongoing) R.string.m3e_lesson_ongoing else R.string.m3e_lesson_next),
                    style = MaterialTheme.typography.labelMedium)
                Text(stringResource(if (ongoing) R.string.m3e_lesson_ends_in else R.string.m3e_lesson_starts_in, duration),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Text(candidate.content.subject, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.m3e_lesson_date_time, stringResource(R.string.m3e_lesson_today),
                candidate.slotLabel, candidate.start.format(timeFormatter), candidate.end.format(timeFormatter)),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
