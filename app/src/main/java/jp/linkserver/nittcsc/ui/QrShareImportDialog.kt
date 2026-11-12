package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import jp.linkserver.nittcsc.ui.theme.appDialogSectionColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.LessonNoteEntity
import jp.linkserver.nittcsc.data.QrSharePayload
import jp.linkserver.nittcsc.data.QrShareSection
import jp.linkserver.nittcsc.logic.QrShareMerge
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.logic.qrShareMerge
import jp.linkserver.nittcsc.logic.qrDayTypesRange
import jp.linkserver.nittcsc.logic.QrImportPeriod
import jp.linkserver.nittcsc.logic.QrImportScopeGroup
import jp.linkserver.nittcsc.logic.qrImportScopeGroups
import jp.linkserver.nittcsc.ui.theme.appCardContainerColor
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface QrImportPage {
    data object Summary : QrImportPage
    data object Details : QrImportPage
    data object Notes : QrImportPage
}
private data class QrImportPreview(val merge: QrShareMerge, val localCounts: Map<QrShareSection, Int>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QrShareImportDialog(
    data: QrSharePayload, state: SchedulerUiState, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onConfirm: (Set<LessonNoteEntity>) -> Unit
) {
    var approvedNotes by remember(data) { mutableStateOf(emptySet<LessonNoteEntity>()) }
    var page by remember(data) { mutableStateOf<QrImportPage>(QrImportPage.Summary) }
    val currentBusy by rememberUpdatedState(busy)
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        confirmValueChange = { it != SheetValue.Hidden || !currentBusy }
    )
    // 重複と置き換え件数の確認はUIスレッドから外す。保存時にも最新のDBで再判定する。
    val preview by produceState<QrImportPreview?>(null, data, state.tasks, state.plans, state.lessonNotes,
        state.lessons, state.cancelledLessons, state.changedLessons, state.dayTypeEntities, state.examDaySchedules) {
        value = null
        value = withContext(Dispatchers.Default) {
            val merge = qrShareMerge(state.tasks, data.tasks, state.plans, data.plans, state.lessonNotes, data.notes)
            val counts = data.sections.associateWith { section ->
                when (section) {
                    QrShareSection.FIRST, QrShareSection.SECOND -> state.lessons.keys.count {
                        it.academicYear == data.academicYear && it.timetableTerm.name == section.name
                    }
                    QrShareSection.CANCELLATIONS -> state.cancelledLessons.count { academicYearForDate(it.first) == data.academicYear }
                    QrShareSection.CHANGES -> state.changedLessons.keys.count { academicYearForDate(it.first) == data.academicYear }
                    QrShareSection.EXAMS -> (state.examDaySchedules.keys + state.dayTypeEntities.values.filter {
                        it.holidaySpecialLabel?.usesExamTimetable == true
                    }.map { it.date }).distinct().count { academicYearForDate(it) == data.academicYear }
                    else -> data.qrDayTypesRange().let { range ->
                        state.dayTypeEntities.keys.count { range == null || it in range }
                    }
                }
            }
            QrImportPreview(merge, counts)
        }
    }
    val merge = preview?.merge
    val selectedReplacements = merge?.noteConflicts?.count { it.current in approvedNotes } ?: 0
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState, sheetGesturesEnabled = !busy,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !busy, shouldDismissOnClickOutside = !busy)
    ) {
        val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.86f }
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page != QrImportPage.Summary) IconButton(onClick = { page = QrImportPage.Summary }, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                }
                Text(stringResource(when (page) {
                    QrImportPage.Summary -> R.string.qr_import_title
                    QrImportPage.Details -> R.string.qr_import_details
                    QrImportPage.Notes -> R.string.qr_import_notes_review
                }), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).semantics { heading() })
            }
            if (page == QrImportPage.Summary) FlowRow(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp)
            ) {
                if (data.classLabel.isNotBlank()) Text(stringResource(R.string.qr_import_source_label, data.classLabel),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.qr_image_year, data.academicYear),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 長文や大きな文字でも本文だけをスクロールし、操作ボタンを常に表示する。
            QrShareNavigation(page, isReturning = { it == QrImportPage.Summary }, modifier = Modifier.weight(1f, fill = false)) { visiblePage ->
                LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (visiblePage) {
                        QrImportPage.Summary -> qrImportSummary(data, merge, selectedReplacements, busy || visiblePage != page,
                            onDetails = { page = QrImportPage.Details }, onNotes = { page = QrImportPage.Notes })
                        QrImportPage.Details -> qrImportDetails(data, state, merge, preview?.localCounts.orEmpty())
                        QrImportPage.Notes -> qrImportNoteChoices(merge?.noteConflicts.orEmpty(), approvedNotes, busy || visiblePage != page) { note, checked ->
                            approvedNotes = if (checked) approvedNotes + note else approvedNotes - note
                        }
                    }
                }
            }
            error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Assertive })
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (page == QrImportPage.Summary) {
                    Button(onClick = { onConfirm(approvedNotes) }, enabled = !busy && merge != null && state.initialized,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.qr_import_busy))
                        } else Text(stringResource(R.string.qr_import_confirm))
                    }
                    TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.qr_cancel))
                    }
                } else Button(onClick = { page = QrImportPage.Summary }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.qr_import_back_to_summary))
                }
            }
        }
    }
}

private fun LazyListScope.qrImportSummary(
    data: QrSharePayload, merge: QrShareMerge?, selectedReplacements: Int, busy: Boolean,
    onDetails: () -> Unit, onNotes: () -> Unit
) {
    val personal = listOf(QrShareSection.TASKS, QrShareSection.PLANS, QrShareSection.NOTES).filter { it in data.sections }
    if (personal.isNotEmpty()) item {
        Column {
            QrImportHeading(stringResource(R.string.qr_import_add))
            personal.forEachIndexed { index, section ->
                val counts = merge?.summary?.let { summary ->
                    when (section) {
                        QrShareSection.TASKS -> summary.addedTasks to summary.duplicateTasks
                        QrShareSection.PLANS -> summary.addedPlans to summary.duplicatePlans
                        else -> summary.addedNotes to summary.duplicateNotes
                    }
                }
                QrImportAdditionRow(section, counts?.first, counts?.second ?: 0)
                if (index < personal.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if (!merge?.noteConflicts.isNullOrEmpty()) item {
        QrImportNavigationRow(stringResource(R.string.qr_import_notes_review),
            stringResource(R.string.qr_import_note_choices, selectedReplacements, merge.noteConflicts.size - selectedReplacements),
            enabled = !busy, onClick = onNotes)
    }
    val scopes = qrImportScopeGroups(data)
    if (scopes.isNotEmpty()) item {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            QrImportHeading(stringResource(R.string.qr_import_replace))
            scopes.forEach { QrImportScope(it) }
        }
    }
    item { QrImportNavigationRow(stringResource(R.string.qr_import_details), enabled = !busy, onClick = onDetails) }
}

@Composable
private fun QrImportHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
}

@Composable
private fun QrImportAdditionRow(section: QrShareSection, added: Int?, duplicates: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(when (section) {
                QrShareSection.TASKS -> Icons.AutoMirrored.Outlined.Assignment
                QrShareSection.PLANS -> Icons.Outlined.Event
                else -> Icons.AutoMirrored.Outlined.Article
            }, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(10.dp).size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(section.labelRes()), style = MaterialTheme.typography.bodyLarge)
            if (duplicates > 0) Text(stringResource(R.string.qr_import_duplicates_skipped, duplicates),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (added == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Text(stringResource(R.string.qr_import_number, added), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun QrImportScope(group: QrImportScopeGroup) {
    val colors = MaterialTheme.colorScheme
    val warning = group.period == QrImportPeriod.ALL_YEARS
    // テーマによって警告面が明色になる場合も、面に対して読めるテーマ色を選ぶ。
    val warningContent = if (qrScopeContrast(colors.onErrorContainer, colors.errorContainer) >= 4.5f) colors.onErrorContainer
        else listOf(colors.onSurface, colors.surface).maxBy { qrScopeContrast(it, colors.errorContainer) }
    val contentColor = if (warning) warningContent else colors.onSurface
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = if (warning) colors.errorContainer else appCardContainerColor(standardColor = colors.surfaceContainerLow),
        contentColor = contentColor
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val tags = listOfNotNull(group.academicYear?.let { stringResource(R.string.qr_image_year, it) },
                    stringResource(when (group.period) {
                        QrImportPeriod.FIRST -> R.string.qr_days_first
                        QrImportPeriod.SECOND -> R.string.qr_days_second
                        QrImportPeriod.YEAR -> R.string.qr_days_year
                        QrImportPeriod.ALL_YEARS -> R.string.qr_import_all_years
                    }))
                tags.forEach { tag ->
                    Surface(shape = CircleShape,
                        color = if (warning) warningContent else colors.primaryContainer,
                        contentColor = if (warning) colors.errorContainer else colors.onPrimaryContainer) {
                        Text(tag, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.sections.forEach { section ->
                    val label = if (section != QrShareSection.DAY_TYPES) section.labelRes() else when (group.period) {
                        QrImportPeriod.FIRST -> R.string.qr_import_first_days_and_breaks
                        QrImportPeriod.SECOND -> R.string.qr_import_second_days_and_breaks
                        QrImportPeriod.YEAR -> R.string.qr_import_year_days_and_breaks
                        QrImportPeriod.ALL_YEARS -> R.string.qr_import_days_and_breaks
                    }
                    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = contentColor)
                }
            }
            group.dayTypesRange?.let { range ->
                Text(stringResource(R.string.qr_days_range, range.start.toString(), range.endInclusive.toString()),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (warning) Text(stringResource(R.string.qr_import_all_years_warning),
                style = MaterialTheme.typography.bodySmall, color = warningContent)
        }
    }
}

private fun qrScopeContrast(foreground: Color, background: Color): Float {
    val light = maxOf(foreground.luminance(), background.luminance())
    val dark = minOf(foreground.luminance(), background.luminance())
    return (light + 0.05f) / (dark + 0.05f)
}

@Composable
private fun QrImportNavigationRow(label: String, description: String? = null, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp),
        color = appDialogSectionColor(MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (description != null) Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
