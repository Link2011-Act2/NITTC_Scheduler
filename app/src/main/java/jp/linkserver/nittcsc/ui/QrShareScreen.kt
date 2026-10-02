package jp.linkserver.nittcsc.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.InternalFeatureFlags
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.QrShareJson
import jp.linkserver.nittcsc.data.QrSharePayload
import jp.linkserver.nittcsc.logic.QrShareCodec
import jp.linkserver.nittcsc.logic.QrShareException
import jp.linkserver.nittcsc.logic.QrShareFailure
import jp.linkserver.nittcsc.qr.QrShareMedia
import jp.linkserver.nittcsc.qr.QrImageEncoding
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class QrPage { HOME, SELECT, DISPLAY, SCAN }

private data class QrDisplayFrame(val index: Int, val bitmap: android.graphics.Bitmap)

@Composable
internal fun QrShareToolbarActions(onShare: () -> Unit, onSearch: () -> Unit) {
    if (InternalFeatureFlags.QR_SHARE_BETA) IconButton(onClick = onShare) {
        Icon(Icons.Filled.QrCode, contentDescription = stringResource(R.string.qr_title))
    }
    IconButton(onClick = onSearch) {
        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search_timetable))
    }
}

internal fun qrFailureMessage(resources: android.content.res.Resources, exception: Exception): String = resources.getString(
    when ((exception as? QrShareException)?.failure) {
        QrShareFailure.VERSION -> R.string.qr_error_version
        QrShareFailure.TOO_LARGE -> R.string.qr_error_large
        QrShareFailure.DIFFERENT_TRANSFER -> R.string.qr_error_different
        QrShareFailure.DAMAGED -> R.string.qr_error_damaged
        QrShareFailure.SETTINGS -> R.string.qr_error_settings
        else -> R.string.qr_error_invalid
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrShareScreen(state: SchedulerUiState, viewModel: SchedulerViewModel, onBack: () -> Unit) {
    if (!InternalFeatureFlags.QR_SHARE_BETA) return
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(QrPage.HOME) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var payload by remember { mutableStateOf<QrSharePayload?>(null) }
    var frames by remember { mutableStateOf(emptyList<String>()) }
    var imageDialog by remember { mutableStateOf(false) }
    var classLabel by remember { mutableStateOf("") }
    fun back() {
        if (busy) return
        error = null
        if (page == QrPage.HOME) onBack() else page = QrPage.HOME
    }
    BackHandler { back() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.qr_title)) }, navigationIcon = {
            IconButton(onClick = ::back, enabled = !busy) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.qr_back))
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            if (busy && page != QrPage.SCAN) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(12.dp))
            when (page) {
                QrPage.HOME -> Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.qr_home_description))
                    Button(onClick = { page = QrPage.SELECT }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.qr_share)) }
                    Button(onClick = { page = QrPage.SCAN }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.qr_read)) }
                }
                QrPage.SELECT -> QrShareSelectionScreen(state, busy) { selection ->
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val data = viewModel.exportQrShare(selection)
                            val encoded = withContext(Dispatchers.Default) { QrShareCodec.create(QrShareJson.encode(data)).frames() }
                            payload = data
                            frames = encoded
                            page = QrPage.DISPLAY
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = qrFailureMessage(resources, e) }
                        finally { busy = false }
                    }
                }
                QrPage.DISPLAY -> QrShareDisplay(frames, busy, onShareImage = { imageDialog = true }, onSelect = { page = QrPage.SELECT })
                QrPage.SCAN -> QrShareScanner(state, viewModel, onBusyChanged = { busy = it })
            }
        }
    }
    if (imageDialog) AlertDialog(
        onDismissRequest = { if (!busy) imageDialog = false },
        title = { Text(stringResource(R.string.qr_image_share)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.qr_image_description))
                OutlinedTextField(value = classLabel, onValueChange = { classLabel = it.take(60).replace("\n", "").replace("\r", "") },
                    label = { Text(stringResource(R.string.qr_class_label)) }, placeholder = { Text(stringResource(R.string.qr_class_hint)) }, singleLine = true, enabled = !busy)
            }
        },
        confirmButton = {
            TextButton(enabled = classLabel.isNotBlank() && !busy, onClick = {
                val data = payload ?: return@TextButton
                busy = true
                error = null
                scope.launch {
                    try {
                        val shared = QrShareMedia.createImage(context, data, classLabel)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = shared.mimeType
                            putExtra(Intent.EXTRA_STREAM, shared.uri)
                            clipData = android.content.ClipData.newUri(context.contentResolver, "QR", shared.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, resources.getString(R.string.qr_image_share)))
                        imageDialog = false
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = resources.getString(R.string.qr_error_image) }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.qr_share)) }
        },
        dismissButton = { TextButton(onClick = { imageDialog = false }, enabled = !busy) { Text(stringResource(R.string.qr_cancel)) } }
    )
}

@Composable
private fun QrShareDisplay(frames: List<String>, busy: Boolean, onShareImage: () -> Unit, onSelect: () -> Unit) {
    var index by remember(frames) { mutableIntStateOf(0) }
    var playing by remember(frames) { mutableStateOf(true) }
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    val frame = frames.getOrNull(index)
    val displayed by produceState<QrDisplayFrame?>(null, frame, index) {
        // 次のQRの生成中も現在の画像を維持し、画像と枚数を同時に切り替える。
        if (frame != null) value = withContext(Dispatchers.Default) { QrDisplayFrame(index, QrShareMedia.qrBitmap(frame)) }
    }
    LaunchedEffect(frames, playing, busy, displayed) {
        val current = displayed
        if (frames.size > 1 && playing && !busy && current != null) {
            // 描画完了後に次へ進む。遅い端末でも生成中のQRを連続でキャンセルしない。
            delay(QrImageEncoding.FRAME_INTERVAL_MS)
            index = (current.index + 1) % frames.size
        }
    }
    val displayedIndex = displayed?.index ?: index
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.qr_display_description))
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            displayed?.let { Image(it.bitmap.asImageBitmap(), contentDescription = stringResource(R.string.qr_part_number, it.index + 1, frames.size), modifier = Modifier.fillMaxSize()) }
                ?: CircularProgressIndicator()
        }
        Text(stringResource(R.string.qr_part_number, displayedIndex + 1, frames.size), style = MaterialTheme.typography.titleMedium)
        if (frames.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { playing = false; index = (index + frames.size - 1) % frames.size }, enabled = !busy) { Text(stringResource(R.string.qr_previous)) }
            TextButton(onClick = { playing = !playing }, enabled = !busy) { Text(stringResource(if (playing) R.string.qr_pause else R.string.qr_play)) }
            TextButton(onClick = { playing = false; index = (index + 1) % frames.size }, enabled = !busy) { Text(stringResource(R.string.qr_next)) }
        }
        Button(onClick = onShareImage, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.qr_image_share)) }
        TextButton(onClick = onSelect, enabled = !busy) { Text(stringResource(R.string.qr_change_selection)) }
    }
}
