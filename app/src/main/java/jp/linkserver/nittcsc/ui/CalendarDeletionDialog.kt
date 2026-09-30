package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.calendar.AppCalendarEventCleaner
import jp.linkserver.nittcsc.calendar.CalendarEventCategory
import jp.linkserver.nittcsc.logic.CalendarDeletionFailure
import jp.linkserver.nittcsc.logic.CalendarDeletionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface CalendarDeleteState {
    data object Counting : CalendarDeleteState
    data class Confirmation(val counts: Map<CalendarEventCategory, Int>) : CalendarDeleteState
    data object Deleting : CalendarDeleteState
    data class Finished(val results: Map<CalendarEventCategory, CalendarDeletionResult>) : CalendarDeleteState
    data class Failed(val failure: CalendarDeletionFailure) : CalendarDeleteState
}

@Composable
internal fun CalendarDeletionDialog(
    clearLessons: Boolean,
    clearDeadlines: Boolean,
    clearReminders: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val categories = remember(clearLessons, clearDeadlines, clearReminders) {
        buildList {
            if (clearLessons) add(CalendarEventCategory.LESSONS)
            if (clearDeadlines) add(CalendarEventCategory.DEADLINES)
            if (clearReminders) add(CalendarEventCategory.REMINDERS)
        }
    }
    val cleaner = remember(context) { AppCalendarEventCleaner(context) }
    var state by remember(categories) { mutableStateOf<CalendarDeleteState>(CalendarDeleteState.Counting) }
    val busy = state == CalendarDeleteState.Counting || state == CalendarDeleteState.Deleting
    val confirmation = state as? CalendarDeleteState.Confirmation
    val finished = state as? CalendarDeleteState.Finished
    val failed = state as? CalendarDeleteState.Failed
    val success = finished?.results?.values?.all { it.isSuccess } == true
    val empty = confirmation?.counts?.values?.sum() == 0
    val retry = failed != null || (finished != null && !success)

    LaunchedEffect(categories, state) {
        try {
            when (state) {
                CalendarDeleteState.Counting -> {
                    val counts = withContext(Dispatchers.IO) { cleaner.count(categories) }
                    state = CalendarDeleteState.Confirmation(counts)
                }
                CalendarDeleteState.Deleting -> {
                    val results = withContext(Dispatchers.IO) { cleaner.delete(categories) }
                    state = CalendarDeleteState.Finished(results)
                }
                else -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state = CalendarDeleteState.Failed(
                if (e is SecurityException) CalendarDeletionFailure.PERMISSION else CalendarDeletionFailure.PROVIDER
            )
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        title = {
            Text(stringResource(when {
                busy -> R.string.dialog_clear_app_calendar_events_title
                confirmation != null -> R.string.dialog_clear_app_calendar_events_confirm_title
                success -> R.string.calendar_delete_success_title
                else -> R.string.calendar_delete_failure_title
            }))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (val current = state) {
                    CalendarDeleteState.Counting, CalendarDeleteState.Deleting -> {
                        CircularProgressIndicator()
                        Text(stringResource(
                            if (current == CalendarDeleteState.Counting) R.string.calendar_delete_counting
                            else R.string.calendar_delete_running
                        ))
                    }
                    is CalendarDeleteState.Confirmation -> {
                        Text(stringResource(
                            if (empty) R.string.calendar_delete_empty
                            else R.string.dialog_clear_app_calendar_events_confirm_message,
                            current.counts.values.sum()
                        ))
                        current.counts.forEach { (category, count) ->
                            Text(stringResource(R.string.calendar_delete_category_count, categoryLabel(category), count))
                        }
                    }
                    is CalendarDeleteState.Finished -> {
                        if (success && current.results.values.all { it.targetCount == 0 }) {
                            Text(stringResource(R.string.calendar_delete_empty))
                        }
                        current.results.forEach { (category, result) ->
                            if (result.deletedCount != null && result.remainingCount != null) {
                                Text(stringResource(
                                    R.string.calendar_delete_category_result,
                                    categoryLabel(category), result.deletedCount, result.remainingCount
                                ))
                            } else {
                                Text(stringResource(R.string.calendar_delete_category_unverified, categoryLabel(category)))
                            }
                            result.failure?.let { Text(deletionFailureMessage(it), color = MaterialTheme.colorScheme.error) }
                        }
                        if (!success && current.results.values.any { (it.remainingCount ?: 0) > 0 }) {
                            Text(stringResource(R.string.calendar_delete_remaining), color = MaterialTheme.colorScheme.error)
                        }
                        Text(stringResource(R.string.calendar_delete_google_sync_note), style = MaterialTheme.typography.bodySmall)
                    }
                    is CalendarDeleteState.Failed -> Text(deletionFailureMessage(current.failure))
                }
            }
        },
        confirmButton = {
            if (!busy) {
                Button(onClick = {
                    when {
                        confirmation != null && !empty -> state = CalendarDeleteState.Deleting
                        retry -> state = if (failed != null) CalendarDeleteState.Counting else CalendarDeleteState.Deleting
                        else -> onDismiss()
                    }
                }) {
                    Text(stringResource(when {
                        confirmation != null && !empty -> R.string.btn_delete_confirm
                        retry -> R.string.calendar_delete_retry
                        else -> R.string.btn_close
                    }))
                }
            }
        },
        dismissButton = {
            if (!busy && ((confirmation != null && !empty) || retry)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(if (confirmation != null) R.string.btn_cancel else R.string.btn_close))
                }
            }
        }
    )
}

@Composable
private fun categoryLabel(category: CalendarEventCategory): String = stringResource(when (category) {
    CalendarEventCategory.LESSONS -> R.string.label_clear_calendar_lessons
    CalendarEventCategory.DEADLINES -> R.string.label_clear_calendar_deadlines
    CalendarEventCategory.REMINDERS -> R.string.label_clear_calendar_reminders
})

@Composable
private fun deletionFailureMessage(failure: CalendarDeletionFailure): String = stringResource(
    if (failure == CalendarDeletionFailure.PERMISSION) R.string.msg_calendar_delete_permission_denied
    else R.string.calendar_delete_provider_failed
)
