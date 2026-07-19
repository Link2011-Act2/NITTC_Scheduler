package jp.linkserver.nittcsc.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.update.AppReleaseNoteInfo
import jp.linkserver.nittcsc.update.AppUpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateOverviewScreen(
    updateInfo: AppUpdateInfo,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val channelLabel = updateInfo.channel.toLocalizedChannelLabel()
    var downloadState by remember { mutableStateOf<ApkDownloadState>(ApkDownloadState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val downloadStatus = downloadState.toStatusText()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.update_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (downloadState is ApkDownloadState.Downloading) {
                                downloadJob?.cancel()
                                downloadJob = null
                                downloadState = ApkDownloadState.Cancelled
                                return@Button
                            }
                            val apkUrl = updateInfo.apkDownloadUrl
                            if (apkUrl == null) {
                                openUrl(context, updateInfo.releaseUrl)
                            } else if (!canRequestPackageInstalls(context)) {
                                openUnknownAppInstallSettings(context)
                                downloadState = ApkDownloadState.WaitingForInstallPermission
                            } else {
                                downloadState = ApkDownloadState.Downloading()
                                downloadJob = scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            downloadApk(
                                                context = context,
                                                downloadUrl = apkUrl,
                                                assetName = updateInfo.apkAssetName ?: "${updateInfo.tagName}.apk",
                                                onProgress = { progress ->
                                                    downloadState = ApkDownloadState.Downloading(progress)
                                                }
                                            )
                                        }
                                    }
                                    result
                                        .onSuccess { apkFile ->
                                            runCatching {
                                                downloadState = ApkDownloadState.OpeningInstaller
                                                openPackageInstaller(context, apkFile)
                                            }.onSuccess {
                                                downloadState = ApkDownloadState.Idle
                                            }.onFailure { error ->
                                                downloadState = ApkDownloadState.Failed(
                                                    error.localizedMessage ?: error.javaClass.simpleName
                                                )
                                            }
                                        }
                                        .onFailure { error ->
                                            downloadState = if (error is CancellationException) {
                                                ApkDownloadState.Cancelled
                                            } else {
                                                ApkDownloadState.Failed(
                                                    error.localizedMessage ?: error.javaClass.simpleName
                                                )
                                            }
                                        }
                                    downloadJob = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = downloadState !is ApkDownloadState.OpeningInstaller &&
                            (updateInfo.apkDownloadUrl != null || updateInfo.releaseUrl.isNotBlank())
                    ) {
                        if (downloadState is ApkDownloadState.Downloading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Text(
                            stringResource(
                                if (downloadState is ApkDownloadState.Downloading) {
                                    R.string.update_cancel_download_button
                                } else {
                                    R.string.update_download_install_button
                                }
                            )
                        )
                    }
                    downloadStatus?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    updateInfo.apkAssetName?.let { assetName ->
                        Text(
                            text = stringResource(R.string.update_asset_name, assetName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        AdaptiveContentPane(
            modifier = Modifier.padding(padding),
            maxWidth = FormContentMaxWidth
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = updateInfo.tagName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                AssistChip(
                    onClick = {},
                    label = { Text(channelLabel) }
                )
            }

            Text(
                text = stringResource(R.string.update_release_notes_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                ReleaseNotesMarkdown(
                    markdown = updateInfo.releaseNotes,
                    emptyText = stringResource(R.string.update_release_notes_empty),
                    modifier = Modifier.padding(16.dp)
                )
            }
            if (updateInfo.intermediateReleaseNotes.isNotEmpty()) {
                IntermediateReleaseNotesSection(updateInfo.intermediateReleaseNotes)
            }
            Spacer(Modifier.height(84.dp))
            }
        }
    }
}

@Composable
private fun IntermediateReleaseNotesSection(
    releaseNotes: List<AppReleaseNoteInfo>
) {
    var expanded by remember(releaseNotes) { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.update_intermediate_release_notes_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            R.string.update_intermediate_release_notes_count,
                            releaseNotes.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    releaseNotes.forEach { releaseNote ->
                        IntermediateReleaseNoteCard(releaseNote)
                    }
                }
            }
        }
    }
}

@Composable
private fun IntermediateReleaseNoteCard(
    releaseNote: AppReleaseNoteInfo
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = releaseNote.tagName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                AssistChip(
                    onClick = {},
                    label = { Text(releaseNote.channel.toLocalizedChannelLabel()) }
                )
            }
            ReleaseNotesMarkdown(
                markdown = releaseNote.releaseNotes,
                emptyText = stringResource(R.string.update_release_notes_empty)
            )
        }
    }
}

private sealed interface ApkDownloadState {
    data object Idle : ApkDownloadState
    data object Cancelled : ApkDownloadState
    data object WaitingForInstallPermission : ApkDownloadState
    data class Downloading(val progress: ApkDownloadProgress? = null) : ApkDownloadState
    data object OpeningInstaller : ApkDownloadState
    data class Failed(val message: String) : ApkDownloadState
}

private data class ApkDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?
)

@Composable
private fun ApkDownloadState.toStatusText(): String? {
    return when (this) {
        ApkDownloadState.Idle -> null
        ApkDownloadState.Cancelled -> stringResource(R.string.update_download_cancelled)
        ApkDownloadState.WaitingForInstallPermission -> stringResource(R.string.update_install_permission_required)
        is ApkDownloadState.Downloading -> progress?.toDisplayText()
            ?: stringResource(R.string.update_downloading)
        ApkDownloadState.OpeningInstaller -> stringResource(R.string.update_opening_installer)
        is ApkDownloadState.Failed -> stringResource(R.string.update_download_failed, message)
    }
}

@Composable
private fun ApkDownloadProgress.toDisplayText(): String {
    val downloadedMb = downloadedBytes.toMegabytesText()
    val total = totalBytes
    return if (total != null && total > 0L) {
        val percent = ((downloadedBytes * 100) / total).coerceIn(0, 100)
        stringResource(
            R.string.update_downloading_progress,
            percent,
            downloadedMb,
            total.toMegabytesText()
        )
    } else {
        stringResource(R.string.update_downloading_progress_unknown_total, downloadedMb)
    }
}

@Composable
private fun ReleaseNotesMarkdown(
    markdown: String,
    emptyText: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .build()
    }
    val markdownText = markdown.ifBlank { emptyText }
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                includeFontPadding = true
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                textSize = 14f
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            textView.textSize = 14f
            markwon.setMarkdown(textView, markdownText)
        }
    )
}

private fun canRequestPackageInstalls(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return true
    }
    return runCatching {
        context.packageManager.canRequestPackageInstalls()
    }.getOrDefault(false)
}

private fun openUnknownAppInstallSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        openUrl(context, "package:${context.packageName}")
    }
}

private fun downloadApk(
    context: Context,
    downloadUrl: String,
    assetName: String,
    onProgress: (ApkDownloadProgress) -> Unit
): File {
    val safeName = assetName
        .substringAfterLast('/')
        .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }
    val updatesDir = File(context.cacheDir, "updates").apply {
        mkdirs()
    }
    val destination = File(updatesDir, safeName)
    val partial = File(updatesDir, "$safeName.part")
    updatesDir.listFiles()
        ?.filter { it.isFile && (it.extension.equals("apk", ignoreCase = true) || it.name.endsWith(".part")) }
        ?.forEach { it.delete() }

    val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "NITTCScheduler-Updater")
    }
    try {
        val status = connection.responseCode
        if (status !in 200..299) {
            error("HTTP $status")
        }
        val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
        connection.inputStream.use { input ->
            partial.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloadedBytes = 0L
                var lastProgressUpdateBytes = 0L
                onProgress(ApkDownloadProgress(downloadedBytes, totalBytes))
                while (true) {
                    if (Thread.currentThread().isInterrupted) {
                        throw CancellationException("Download cancelled")
                    }
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    if (
                        downloadedBytes == totalBytes ||
                        downloadedBytes - lastProgressUpdateBytes >= PROGRESS_UPDATE_STEP_BYTES
                    ) {
                        onProgress(ApkDownloadProgress(downloadedBytes, totalBytes))
                        lastProgressUpdateBytes = downloadedBytes
                    }
                }
            }
        }
        if (destination.exists()) destination.delete()
        check(partial.renameTo(destination)) { "Could not finalize APK" }
        return destination
    } catch (error: CancellationException) {
        if (destination.exists()) destination.delete()
        throw error
    } finally {
        connection.disconnect()
        if (partial.exists()) partial.delete()
    }
}

private const val PROGRESS_UPDATE_STEP_BYTES = 256L * 1024L

private fun Long.toMegabytesText(): String {
    return String.format(Locale.US, "%.1f", this / (1024f * 1024f))
}

private fun openPackageInstaller(context: Context, apkFile: File) {
    val apkUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        apkFile
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apkUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val packageManager = context.packageManager
    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.resolveActivity(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }
    check(resolved != null) { "パッケージインストーラーを起動できませんでした" }
    context.startActivity(intent)
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
    }
}

@Composable
private fun String.toLocalizedChannelLabel(): String {
    return when {
        equals("IntDev", ignoreCase = true) -> stringResource(R.string.about_dev_channel_value_intdev)
        equals("Beta", ignoreCase = true) -> stringResource(R.string.about_dev_channel_value_beta)
        equals("PreRelease", ignoreCase = true) -> stringResource(R.string.about_dev_channel_value_prerelease)
        equals("Release", ignoreCase = true) || equals("Stable", ignoreCase = true) ->
            stringResource(R.string.about_dev_channel_value_stable)
        else -> stringResource(R.string.about_dev_channel_value_unknown)
    }
}
