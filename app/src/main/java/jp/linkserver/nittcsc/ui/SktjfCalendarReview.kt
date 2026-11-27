package jp.linkserver.nittcsc.ui

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.sktjf.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun SktjfCalendarReview(
    preview: SktjfApplyPreview,
    choices: MutableMap<LocalDate, SktjfConflictChoice>,
    pending: Boolean,
) {
    val resources = LocalResources.current
    val conflicts = remember(preview) { preview.calendarConflicts.associateBy { it.date } }
    val firstMonth = YearMonth.from(preview.document.start)
    val lastMonth = YearMonth.from(preview.document.end)
    var month by remember(preview) { mutableStateOf(YearMonth.from(conflicts.keys.minOrNull())) }
    var selectedDate by remember(preview) { mutableStateOf<LocalDate?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(enabled = month > firstMonth, onClick = { month = month.minusMonths(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.cd_previous_month))
        }
        Text(stringResource(R.string.sktjf_calendar_review_month, month.year, month.monthValue),
            style = MaterialTheme.typography.titleMedium)
        IconButton(enabled = month < lastMonth, onClick = { month = month.plusMonths(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.cd_next_month))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        resources.getStringArray(R.array.sktjf_weekdays).forEach { day ->
            Text(day, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
        }
    }
    CalendarMonthGrid(month, DayOfWeek.MONDAY, fixedWeeks = false) { date, modifier ->
        val conflict = conflicts[date]
        val incomingSelected = choices[date] == SktjfConflictChoice.APPLY_INCOMING
        val value = if (conflict == null) preview.calendarDays[date] else if (incomingSelected) conflict.incoming else conflict.current
        val colors = MaterialTheme.colorScheme
        val visual = when {
            conflict != null && incomingSelected -> DayTypeVisual(colors.primaryContainer, colors.onPrimaryContainer)
            conflict != null -> DayTypeVisual(colors.errorContainer, colors.onErrorContainer)
            value?.dayType == SktjfDayType.A -> dayTypeVisual(DayType.A)
            value?.dayType == SktjfDayType.B -> dayTypeVisual(DayType.B)
            else -> dayTypeVisual(DayType.HOLIDAY)
        }
        val action = if (conflict == null) Modifier else Modifier
            .clickable(enabled = !pending, role = Role.Button) { selectedDate = date }
            .semantics { selected = incomingSelected }
        Surface(modifier.then(action).testTag("sktjf-date-$date"), shape = AbDayCellShape,
            color = visual.container, contentColor = visual.content) {
            Column(Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium)
                if (conflict == null) {
                    CalendarReviewCellLabel(calendarCellValue(resources, value))
                } else {
                    CalendarReviewCellLabel(calendarCellValue(resources, conflict.current))
                    CalendarReviewCellLabel(if (incomingSelected) "↓ ✓" else "↓")
                    CalendarReviewCellLabel(calendarCellValue(resources, conflict.incoming))
                }
            }
        }
    }
    selectedDate?.let { date ->
        val conflict = conflicts.getValue(date)
        val adopt = choices[date] == SktjfConflictChoice.APPLY_INCOMING
        AlertDialog(onDismissRequest = { selectedDate = null },
            title = { Text(formatDateForDisplay(date, true)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.sktjf_current_value, calendarDetails(resources, conflict.current)))
                    Text(stringResource(R.string.sktjf_incoming_value, calendarDetails(resources, conflict.incoming)))
                    ChoiceRow(R.string.sktjf_merge_chosen_current, !adopt, !pending) { choices[date] = SktjfConflictChoice.KEEP_LOCAL }
                    ChoiceRow(R.string.sktjf_merge_chosen_incoming, adopt, !pending && conflict.projection is SktjfCalendarProjection.Supported) {
                        choices[date] = SktjfConflictChoice.APPLY_INCOMING
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDate = null }) { Text(stringResource(R.string.sktjf_merge_close)) }
            })
    }
}

@Composable
private fun CalendarReviewCellLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center)
}

private fun calendarCellValue(resources: Resources, day: SktjfCalendarContent?): String =
    day?.event?.name ?: calendarDetails(resources, day)
