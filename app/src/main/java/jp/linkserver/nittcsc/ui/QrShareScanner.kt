package jp.linkserver.nittcsc.ui

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.os.SystemClock
import androidx.camera.view.PreviewView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.QrShareJson
import jp.linkserver.nittcsc.data.QrSharePayload
import jp.linkserver.nittcsc.data.QrShareSection
import jp.linkserver.nittcsc.logic.QrShareCollector
import jp.linkserver.nittcsc.logic.QrShareException
import jp.linkserver.nittcsc.logic.QrShareFailure
import jp.linkserver.nittcsc.logic.QrScanTarget
import jp.linkserver.nittcsc.logic.QrScannerTracker
import jp.linkserver.nittcsc.logic.QrScannerPhase
import jp.linkserver.nittcsc.logic.QrScannerVisualState
import jp.linkserver.nittcsc.qr.QrShareMedia
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun QrShareScanner(state: SchedulerUiState, viewModel: SchedulerViewModel, onBusyChanged: (Boolean) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var collector by remember { mutableStateOf(QrShareCollector()) }
    var received by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var receivedFragmentIndices by remember { mutableStateOf(emptySet<Int>()) }
    // pulseの時刻だけを保持する。uniqueかどうかはCollectorの受信数で判定する。
    var fragmentReceiptTimes by remember { mutableStateOf(emptyMap<Int, Long>()) }
    var pending by remember { mutableStateOf<QrSharePayload?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    var readingFile by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    var target by remember { mutableStateOf<QrScanTarget?>(null) }
    var tracker by remember { mutableStateOf(QrScannerTracker()) }
    var detectedAt by remember { mutableLongStateOf(0L) }
    var completionShown by remember { mutableStateOf(false) }
    var failureShown by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var hasCameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPermission = it }
    fun reset() {
        collector = QrShareCollector()
        received = 0; total = 0; pending = null; error = null; success = null
        receivedFragmentIndices = emptySet(); fragmentReceiptTimes = emptyMap()
        target = null; tracker = QrScannerTracker(); detectedAt = 0L; completionShown = false; failureShown = false
    }
    suspend fun accept(text: String): Boolean {
        // エラー後のフレームで表示を消さない。GIFの後続解析もここで止める。
        if (error != null || pending != null) return true
        return try {
            val session = collector
            val data = withContext(Dispatchers.Default) { session.add(text)?.let(QrShareJson::decode) }
            if (session.received > received) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                receivedFragmentIndices = session.receivedFragmentIndices
                session.lastReceivedIndex?.let { fragmentReceiptTimes = fragmentReceiptTimes + (it to SystemClock.elapsedRealtime()) }
            }
            received = session.received; total = session.total
            if (data != null) pending = data
            error = null
            pending != null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            error = qrFailureMessage(resources, e)
            if ((e as? QrShareException)?.failure != QrShareFailure.DIFFERENT_TRANSFER) {
                collector = QrShareCollector(); received = 0; total = 0
                receivedFragmentIndices = emptySet(); fragmentReceiptTimes = emptyMap()
            }
            true
        }
    }
    fun readPickedImage(uri: android.net.Uri?) {
        if (uri != null && error == null && !processing && !readingFile && !importing && pending == null) {
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
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> readPickedImage(uri) }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) readPickedImage(result.data?.data)
    }
    LaunchedEffect(detectedAt, pending) {
        if (pending == null) { delay(QrScannerTracker.GRACE_MS + 1); target = tracker.current(SystemClock.elapsedRealtime()) }
    }
    LaunchedEffect(pending) {
        completionShown = false
        if (pending != null) {
            delay(if (ValueAnimator.areAnimatorsEnabled()) 1_350L else 160L)
            completionShown = true
        }
    }
    LaunchedEffect(error, pending, active) {
        failureShown = false
        if (error != null && pending == null && active) {
            // Soft Burstとバツの演出を見せてから警告を出す。解析の停止はerrorで即時に行う。
            delay(if (ValueAnimator.areAnimatorsEnabled()) 700L else 160L)
            failureShown = true
        }
    }
    LaunchedEffect(success) { success?.let { snackbar.showSnackbar(it) } }
    val visual = QrScannerVisualState(when {
        pending != null -> QrScannerPhase.Completed
        error != null -> QrScannerPhase.Failed
        cameraError -> QrScannerPhase.Error
        received > 0 -> QrScannerPhase.Receiving
        target != null -> QrScannerPhase.Tracking
        else -> QrScannerPhase.Idle
    }, target, received, total)
    QrScannerChrome(visual = visual, preview = preview, active = active,
        fragmentSession = collector, receivedFragmentIndices = receivedFragmentIndices, fragmentReceiptTimes = fragmentReceiptTimes,
        hasPermission = hasCameraPermission, cameraError = cameraError,
        loading = readingFile || importing,
        // 認識の猶予期間も無効化を保ち、フレームごとの復号終了で点滅させない。
        galleryEnabled = error == null && target == null && !readingFile && !processing && !importing && pending == null,
        backEnabled = !readingFile && !importing, snackbar = snackbar, onBack = onBack,
        onPermission = { permission.launch(Manifest.permission.CAMERA) }, onRetryCamera = { cameraError = false },
        onGallery = {
            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                try {
                    galleryPicker.launch(Intent(Intent.ACTION_PICK).apply {
                        setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                    })
                } catch (_: ActivityNotFoundException) {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        }, camera = {
            QrCameraPreview(Modifier.fillMaxSize(), analysisEnabled = active && error == null && !readingFile && !importing && pending == null,
                onFrame = { text ->
                    if (error == null && !processing && !readingFile && !importing && pending == null) {
                        processing = true
                        scope.launch { try { accept(text) } finally { processing = false } }
                    }
                }, onDetection = { corners ->
                    if (error == null && pending == null) {
                        val now = SystemClock.elapsedRealtime()
                        tracker.detect(corners, now)?.let { target = it; detectedAt = now }
                    }
                }, onPreviewReady = { preview = it }, onError = { cameraError = true })
        })
    error?.takeIf { failureShown && pending == null && !processing && !readingFile && !importing }?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.qr_scan_failed)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = ::reset) { Text(stringResource(R.string.btn_ok)) } }
        )
    }
    pending?.takeIf { completionShown }?.let { data ->
        QrShareImportDialog(data, state, busy = importing || processing, error = error,
            onDismiss = ::reset, onConfirm = { approvedNotes ->
                importing = true
                onBusyChanged(true)
                error = null
                scope.launch {
                    try {
                        val result = viewModel.importQrShare(context, data, approvedNotes)
                        reset()
                        val message = resources.getString(if (result.integrationsFailed) R.string.qr_import_partial else R.string.qr_import_success)
                        success = if (data.sections.any { it == QrShareSection.TASKS || it == QrShareSection.PLANS || it == QrShareSection.NOTES }) {
                            result.summary.let { summary -> message + "\n" + resources.getString(R.string.qr_import_result,
                                summary.added, summary.duplicates, summary.replacedNotes, summary.keptNotes) +
                                if (summary.unlinkedItems > 0) "\n" + resources.getString(R.string.qr_import_unlinked, summary.unlinkedItems) else "" }
                        } else message
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = qrFailureMessage(resources, e) }
                    finally { importing = false; onBusyChanged(false) }
                }
            }
        )
    }
}
