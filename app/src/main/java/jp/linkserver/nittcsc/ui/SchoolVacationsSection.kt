package jp.linkserver.nittcsc.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.SchoolVacationEntity
import jp.linkserver.nittcsc.data.SchoolVacationPeriod
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.reminder.NotificationScheduleCoordinator
import jp.linkserver.nittcsc.sktjf.SchoolVacationCheck
import jp.linkserver.nittcsc.sktjf.SchoolVacationPreview
import jp.linkserver.nittcsc.sktjf.pendingSchoolVacationIds
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModel
import jp.linkserver.nittcsc.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

@Composable
internal fun SchoolVacationUpdateNotice(viewModel: SchedulerViewModel, resumeRequest: Long) {
    val context = LocalContext.current
    val year = academicYearForDate(rememberCurrentDate())
    val pending by viewModel.pendingSchoolVacationUpdate.collectAsState()
    var announcedRevision by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(year, resumeRequest) { viewModel.refreshPendingSchoolVacationUpdate(year) }
    LaunchedEffect(pending?.revision) {
        val revision = pending?.revision ?: return@LaunchedEffect
        if (revision != announcedRevision) {
            announcedRevision = revision
            Toast.makeText(context, R.string.sktjf_vacations_update_available, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
internal fun SchoolVacationsSection(viewModel: SchedulerViewModel, year: Int) {
    val vacations by viewModel.schoolVacations.collectAsState(initial = emptyList())
    val pendingUpdate by viewModel.pendingSchoolVacationUpdate.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var preview by remember(year) { mutableStateOf<SchoolVacationPreview?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var editing by remember { mutableStateOf<SchoolVacationEntity?>(null) }
    var removing by remember { mutableStateOf<SchoolVacationEntity?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember(year) { mutableStateOf<Int?>(null) }
    var availableUpdate by remember(year) { mutableStateOf<SchoolVacationPreview?>(null) }
    val hasPreviousRelease = vacations.isNotEmpty()

    fun openPreview(plan: SchoolVacationPreview, manual: Boolean) {
        selected = plan.changes.filter { it.defaultSelected(manual) }.mapTo(mutableSetOf()) { it.candidate.id }
        preview = plan
    }

    LaunchedEffect(year, hasPreviousRelease) {
        if (hasPreviousRelease) {
            val result = viewModel.checkSchoolVacations(year)
            if (result is SchoolVacationCheck.Ready && result.preview.changes.any {
                it.previous?.revision != result.preview.revision
            }) availableUpdate = result.preview
        }
    }

    suspend fun finish(changed: Boolean) {
        status = if (changed) R.string.sktjf_vacations_saved else R.string.sktjf_vacations_stale
        if (changed) {
            NotificationScheduleCoordinator.refresh(context)
            WidgetUpdater.updateAll(context)
        }
    }

    fun check(targetYear: Int) {
        busy = true
        status = R.string.sktjf_vacations_loading
        scope.launch {
            try {
                when (val result = viewModel.checkSchoolVacations(targetYear)) {
                    is SchoolVacationCheck.Ready -> {
                        status = if (result.preview.changes.isEmpty()) R.string.sktjf_vacations_no_changes else null
                        if (result.preview.changes.isNotEmpty()) {
                            openPreview(result.preview, manual = true)
                        }
                    }
                    SchoolVacationCheck.NotPublished -> status = R.string.sktjf_vacations_unavailable
                    SchoolVacationCheck.Failed -> status = R.string.sktjf_vacations_error
                }
            } finally {
                busy = false
            }
        }
    }

    TextButton(enabled = !busy, onClick = { check(year) }) { Text(stringResource(R.string.sktjf_vacations_check)) }
    if (year + 1 in pendingSchoolVacationIds(vacations, year) || vacations.any {
        it.id == "$year-year_end" && it.local != null && it.local.end == null
    }) {
        TextButton(enabled = !busy, onClick = { check(year + 1) }) {
            Text(stringResource(R.string.sktjf_vacations_check_next_year))
        }
    }
    (pendingUpdate ?: availableUpdate)?.let { plan ->
        TextButton(enabled = !busy, onClick = { openPreview(plan, manual = false) }) {
            Text(stringResource(R.string.sktjf_vacations_update))
        }
    }
    status?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }

    vacations.forEach { vacation ->
        vacation.local?.let { period ->
            Text(vacationLabel(period))
            if (period.end == null) {
                Text(stringResource(R.string.sktjf_vacations_confirmed_through,
                    period.confirmedThrough.format(vacationDateFormatter)), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.sktjf_vacations_pending), style = MaterialTheme.typography.bodySmall)
            }
            Row {
                TextButton(enabled = !busy, onClick = { editing = vacation }) { Text(stringResource(R.string.sktjf_vacations_edit)) }
                TextButton(enabled = !busy, onClick = { removing = vacation }) { Text(stringResource(R.string.btn_delete)) }
            }
        }
    }

    removing?.let { vacation ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.sktjf_vacations_remove, vacation.local?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch { finish(viewModel.editSchoolVacation(vacation, null)) }
                }) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }
    editing?.let { vacation ->
        val period = requireNotNull(vacation.local)
        AddBreakDialog(
            initialName = period.name, initialStart = period.start,
            initialEnd = period.confirmedThrough, editing = true,
            onDismiss = { editing = null },
            onSave = { name, start, end ->
                editing = null
                scope.launch {
                    val unchangedEnd = period.end == null && end == period.confirmedThrough
                    finish(viewModel.editSchoolVacation(vacation, SchoolVacationPeriod(
                        name.trim(), start, if (unchangedEnd) null else end, end,
                        period.estimatedEndMonth.takeIf { unchangedEnd }
                    )))
                }
            }
        )
    }

    preview?.let { plan ->
        AlertDialog(
            onDismissRequest = { if (!busy) preview = null },
            title = { Text(stringResource(R.string.sktjf_vacations_review)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    plan.changes.forEach { change ->
                        val id = change.candidate.id
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = id in selected, enabled = !busy, onCheckedChange = {
                                selected = if (it) selected + id else selected - id
                            })
                            Column {
                                Text(stringResource(R.string.sktjf_vacations_adopt, vacationLabel(change.candidate.base)))
                                change.previous?.let {
                                    Text(stringResource(R.string.sktjf_vacations_current,
                                        it.local?.let { period -> vacationLabel(period) } ?: stringResource(R.string.label_not_registered)))
                                }
                                if (change.teachingDates.isNotEmpty()) {
                                    Text(stringResource(R.string.sktjf_vacations_teaching_days, change.teachingDates.joinToString("、")),
                                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            finish(viewModel.applySchoolVacations(plan, selected))
                            preview = null
                            availableUpdate = null
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(R.string.sktjf_vacations_apply)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { preview = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

private val vacationDateFormatter = DateTimeFormatter.ofPattern("M月d日")
private val vacationYearDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

@Composable
private fun vacationLabel(period: SchoolVacationPeriod): String {
    val crossesYear = period.end?.year?.let { it != period.start.year } == true
    val startFormat = if (period.end == null || crossesYear) vacationYearDateFormatter else vacationDateFormatter
    val end = period.end?.format(if (crossesYear) vacationYearDateFormatter else vacationDateFormatter)
        ?: period.estimatedEndMonth?.let { stringResource(R.string.sktjf_vacations_estimated_month, it) }
        ?: stringResource(R.string.sktjf_vacations_unknown)
    return stringResource(R.string.sktjf_vacations_period, period.name, period.start.format(startFormat), end)
}
