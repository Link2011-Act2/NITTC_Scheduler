package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            key(page) {
                LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (page) {
                        QrImportPage.Summary -> qrImportSummary(data, merge, selectedReplacements, busy,
                            onDetails = { page = QrImportPage.Details }, onNotes = { page = QrImportPage.Notes })
                        QrImportPage.Details -> qrImportDetails(data, state, merge, preview?.localCounts.orEmpty())
                        QrImportPage.Notes -> qrImportNoteChoices(merge?.noteConflicts.orEmpty(), approvedNotes, busy) { note, checked ->
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
    val yearSections = listOf(QrShareSection.FIRST, QrShareSection.SECOND, QrShareSection.CANCELLATIONS,
        QrShareSection.CHANGES, QrShareSection.EXAMS).filter { it in data.sections }
    if (yearSections.isNotEmpty() || QrShareSection.DAY_TYPES in data.sections) item {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            QrImportHeading(stringResource(R.string.qr_import_replace))
            if (yearSections.isNotEmpty()) {
                val bothTerms = QrShareSection.FIRST in yearSections && QrShareSection.SECOND in yearSections
                val timetableLabels = if (bothTerms) listOf(stringResource(R.string.qr_import_both_terms))
                    else yearSections.filter { it == QrShareSection.FIRST || it == QrShareSection.SECOND }
                        .map { stringResource(it.labelRes()) }
                val otherLabels = yearSections.filter { it != QrShareSection.FIRST && it != QrShareSection.SECOND }
                    .map { stringResource(it.labelRes()) }
                QrImportScope(stringResource(R.string.qr_image_year, data.academicYear),
                    listOf(timetableLabels.joinToString("・"), otherLabels.joinToString("・")).filter { it.isNotEmpty() }.joinToString("\n"))
            }
            if (QrShareSection.DAY_TYPES in data.sections) {
                val range = data.qrDayTypesRange()
                QrImportScope(
                    if (range == null) stringResource(R.string.qr_import_all_years)
                    else stringResource(data.dayTypesScope.labelRes()),
                    stringResource(R.string.qr_import_days_and_breaks),
                    warning = if (range == null) stringResource(R.string.qr_import_all_years_warning) else null
                )
                if (range != null) Text(stringResource(R.string.qr_days_range, range.start.toString(), range.endInclusive.toString()),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
private fun QrImportScope(tag: String, text: String, warning: String? = null) {
    val colors = MaterialTheme.colorScheme
    // 動的テーマの警告背景はダークモードでも明色。薄めず、暗い本文色でコントラストを保つ。
    val lightWarningOnDark = colors.surface.luminance() < 0.5f && colors.errorContainer.luminance() > 0.5f
    Surface(shape = RoundedCornerShape(16.dp), color = if (warning == null) colors.surfaceContainerLow
        else colors.errorContainer.copy(alpha = if (lightWarningOnDark) 1f else 0.5f)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(if (warning == null) 0.dp else 12.dp)) {
            val badge: @Composable () -> Unit = {
                Surface(shape = RoundedCornerShape(12.dp), color = if (warning == null) colors.primaryContainer else colors.errorContainer) {
                    Text(tag, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            val description: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text, style = MaterialTheme.typography.bodyMedium,
                        color = if (warning != null && lightWarningOnDark) colors.surface else colors.onSurface)
                    if (warning != null) Text(warning, style = MaterialTheme.typography.bodySmall, color = colors.onErrorContainer)
                }
            }
            if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { badge(); description() }
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                badge()
                Column(Modifier.weight(1f)) { description() }
            }
        }
    }
}

@Composable
private fun QrImportNavigationRow(label: String, description: String? = null, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
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
