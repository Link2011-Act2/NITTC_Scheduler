package jp.linkserver.nittcsc.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.QrShareJson
import jp.linkserver.nittcsc.data.QrSharePayload
import jp.linkserver.nittcsc.data.QrShareSection
import jp.linkserver.nittcsc.logic.QrShareCollector
import jp.linkserver.nittcsc.logic.QrShareException
import jp.linkserver.nittcsc.logic.QrShareFailure
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.qr.QrShareMedia
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun QrShareScanner(state: SchedulerUiState, viewModel: SchedulerViewModel, onBusyChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var collector by remember { mutableStateOf(QrShareCollector()) }
    var received by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var missing by remember { mutableStateOf(emptyList<Int>()) }
    var pending by remember { mutableStateOf<QrSharePayload?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    var readingFile by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var hasCameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPermission = it }
    fun reset() {
        collector = QrShareCollector()
        received = 0; total = 0; missing = emptyList(); pending = null; error = null; success = null
    }
    suspend fun accept(text: String): Boolean {
        return try {
            val session = collector
            val data = withContext(Dispatchers.Default) { session.add(text)?.let(QrShareJson::decode) }
            if (session.received > received) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            received = session.received; total = session.total; missing = session.missing
            if (data != null) pending = data
            error = null
            pending != null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            error = qrFailureMessage(resources, e)
            if ((e as? QrShareException)?.failure != QrShareFailure.DIFFERENT_TRANSFER) {
                collector = QrShareCollector(); received = 0; total = 0; missing = emptyList()
            }
            false
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !processing && !importing && pending == null) {
            readingFile = true
            onBusyChanged(true)
            scope.launch {
                try { QrShareMedia.readImage(context, uri) { frame -> withContext(Dispatchers.Main) { accept(frame) } } }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = qrFailureMessage(resources, e) }
                finally { readingFile = false; onBusyChanged(false) }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.qr_scan_description))
        if (hasCameraPermission && !cameraError && pending == null && !readingFile && !importing && success == null) {
            QrCameraPreview(Modifier.fillMaxWidth().weight(1f).heightIn(min = 160.dp), onFrame = { text ->
                if (!processing && !readingFile && !importing && pending == null) {
                    processing = true
                    scope.launch { try { accept(text) } finally { processing = false } }
                }
            }, onError = { cameraError = true })
        } else if (!hasCameraPermission) {
            Text(stringResource(R.string.qr_camera_permission))
            TextButton(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.qr_enable_camera)) }
        }
        if (cameraError) Text(stringResource(R.string.qr_camera_error), color = MaterialTheme.colorScheme.error)
        if (readingFile || importing) CircularProgressIndicator()
        Text(stringResource(R.string.qr_received, received, total), style = MaterialTheme.typography.titleMedium)
        if (missing.isNotEmpty()) Text(stringResource(R.string.qr_missing, missing.take(24).joinToString(", ") + if (missing.size > 24) "…" else ""), style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        success?.let { Text(it) }
        Button(onClick = { imagePicker.launch(arrayOf("image/*")) }, enabled = !readingFile && !processing && !importing && pending == null, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.qr_read_image))
        }
        TextButton(onClick = { reset(); cameraError = false }, enabled = !readingFile && !processing && !importing) { Text(stringResource(R.string.qr_reset)) }
    }
    pending?.let { data ->
        AlertDialog(
            onDismissRequest = { if (!importing) reset() },
            title = { Text(stringResource(R.string.qr_import_title)) },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (data.classLabel.isNotBlank()) Text(data.classLabel, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.qr_image_year, data.academicYear))
                    Text(stringResource(R.string.qr_import_warning))
                    data.sections.sortedBy { it.ordinal }.forEach { section ->
                        val localCount = when (section) {
                            QrShareSection.FIRST -> state.lessons.keys.count { it.academicYear == data.academicYear && it.timetableTerm.name == "FIRST" }
                            QrShareSection.SECOND -> state.lessons.keys.count { it.academicYear == data.academicYear && it.timetableTerm.name == "SECOND" }
                            QrShareSection.CANCELLATIONS -> state.cancelledLessons.count { academicYearForDate(it.first) == data.academicYear }
                            QrShareSection.CHANGES -> state.changedLessons.keys.count { academicYearForDate(it.first) == data.academicYear }
                            QrShareSection.DAY_TYPES -> state.dayTypeEntities.size
                            QrShareSection.NOTES -> state.lessonNotes.size
                            QrShareSection.PLANS -> state.plans.size
                            QrShareSection.TASKS -> state.tasks.size
                        }
                        Text(stringResource(R.string.qr_import_count, stringResource(section.labelRes()), localCount, data.count(section)))
                        Text(stringResource(when (section) {
                            QrShareSection.FIRST, QrShareSection.SECOND -> R.string.qr_import_timetable_scope
                            QrShareSection.CANCELLATIONS, QrShareSection.CHANGES -> R.string.qr_import_year_scope
                            else -> R.string.qr_import_all_scope
                        }), style = MaterialTheme.typography.bodySmall)
                    }
                    if (QrShareSection.DAY_TYPES in data.sections) Text(stringResource(R.string.qr_import_breaks, state.longBreaks.size, data.longBreaks.size))
                    Text(stringResource(R.string.qr_reminder_notice), style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(enabled = !importing && !processing, onClick = {
                importing = true
                onBusyChanged(true)
                error = null
                scope.launch {
                    try {
                        val warning = viewModel.importQrShare(context, data)
                        reset()
                        success = resources.getString(if (warning) R.string.qr_import_partial else R.string.qr_import_success)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = qrFailureMessage(resources, e) }
                    finally { importing = false; onBusyChanged(false) }
                }
            }) { Text(stringResource(R.string.qr_overwrite)) } },
            dismissButton = { TextButton(onClick = ::reset, enabled = !importing) { Text(stringResource(R.string.qr_cancel)) } }
        )
    }
}
