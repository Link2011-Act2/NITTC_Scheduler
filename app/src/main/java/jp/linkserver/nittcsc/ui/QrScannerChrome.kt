package jp.linkserver.nittcsc.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.logic.QrScannerPhase
import jp.linkserver.nittcsc.logic.QrScannerVisualState

@Composable
internal fun QrScannerChrome(
    visual: QrScannerVisualState,
    preview: PreviewView?,
    active: Boolean,
    fragmentSession: Any,
    receivedFragmentIndices: Set<Int>,
    fragmentReceiptTimes: Map<Int, Long>,
    hasPermission: Boolean,
    cameraError: Boolean,
    loading: Boolean,
    galleryEnabled: Boolean,
    backEnabled: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onGallery: () -> Unit,
    onPermission: () -> Unit,
    onRetryCamera: () -> Unit,
    camera: @Composable () -> Unit
) {
    ScannerSystemBars()
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(colors.surface)) {
        if (hasPermission && !cameraError) camera()
        if ((hasPermission && !cameraError) || visual.phase == QrScannerPhase.Completed || visual.phase == QrScannerPhase.Failed || visual.received > 0)
            QrScannerVisuals(visual, preview, active)
        Row(Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(colors.surface.copy(alpha = 0.78f), colors.surface.copy(alpha = 0f))))
            .windowInsetsPadding(WindowInsets.statusBars).padding(start = 8.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = backEnabled) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.qr_back), tint = colors.onSurface)
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val titleMaxWidth = if (visual.total > 0) maxWidth * 0.65f else maxWidth
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.qr_title), style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = titleMaxWidth))
                    if (visual.total > 0) {
                        key(fragmentSession) {
                            QrFragmentDotIndicator(visual.total, receivedFragmentIndices, fragmentReceiptTimes, active,
                                visual.phase == QrScannerPhase.Completed, Modifier.weight(1f).padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        if ((!hasPermission || cameraError) && visual.phase != QrScannerPhase.Completed && visual.phase != QrScannerPhase.Failed) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(if (cameraError) R.string.qr_camera_error else R.string.qr_camera_permission),
                    style = MaterialTheme.typography.bodyLarge)
                Button(onClick = if (cameraError) onRetryCamera else onPermission) {
                    Text(stringResource(if (cameraError) R.string.qr_scan_retry_camera else R.string.qr_enable_camera))
                }
            }
        }
        if (hasPermission && !cameraError && visual.phase == QrScannerPhase.Idle) {
            Text(stringResource(R.string.qr_scan_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 24.dp, end = 96.dp, bottom = 38.dp)
                    .background(colors.surface.copy(alpha = 0.75f), MaterialTheme.shapes.large).padding(12.dp))
        }
        val galleryDescription = stringResource(R.string.qr_read_image)
        FilledTonalIconButton(onClick = onGallery, enabled = galleryEnabled,
            modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.navigationBars).padding(20.dp).size(64.dp)
                .semantics { contentDescription = galleryDescription }) {
            if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 100.dp))
    }
}

@Suppress("DEPRECATION")
@Composable
private fun ScannerSystemBars() {
    val view = LocalView.current
    val activity = LocalContext.current.activity()
    val lightStatusBar = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    DisposableEffect(activity, view, lightStatusBar) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val statusAppearance = controller?.isAppearanceLightStatusBars
        val navigationAppearance = controller?.isAppearanceLightNavigationBars
        val statusColor = window?.statusBarColor
        val navigationColor = window?.navigationBarColor
        val contrast = if (Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced else null
        controller?.isAppearanceLightStatusBars = lightStatusBar
        controller?.isAppearanceLightNavigationBars = false
        window?.statusBarColor = android.graphics.Color.TRANSPARENT
        window?.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced = false
        onDispose {
            statusAppearance?.let { controller.isAppearanceLightStatusBars = it }
            navigationAppearance?.let { controller.isAppearanceLightNavigationBars = it }
            statusColor?.let { window.statusBarColor = it }
            navigationColor?.let { window.navigationBarColor = it }
            if (Build.VERSION.SDK_INT >= 29 && contrast != null) window?.isNavigationBarContrastEnforced = contrast
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
