package jp.linkserver.nittcsc.ui

import jp.linkserver.nittcsc.TrickuildMetadata

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.linkserver.pyrite.about.AboutUpdateCard
import jp.linkserver.pyrite.about.AboutAction
import jp.linkserver.pyrite.about.AboutActionGroup
import jp.linkserver.pyrite.screen.PyriteScreenScaffold
import jp.linkserver.pyrite.screen.PyriteBackButton
import jp.linkserver.pyrite.theme.PyriteTheme
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.LocalUiDesignMode
import androidx.compose.foundation.layout.PaddingValues
import jp.linkserver.nittcsc.R
import jp.linkserver.goldy.*
import jp.linkserver.nittcsc.update.detectReleaseChannel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onCheckForUpdates: suspend () -> CheckOutcome,
    onOssLicenses: () -> Unit = {},
    onUpdateAvailable: (UpdateCandidate) -> Unit = {},
    availableUpdate: UpdateCandidate? = null,
    onOpenUpdateCenter: () -> Unit = {},
    hasSavedUpdate: Boolean = false
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val (versionName, versionCode) = remember { resolveAppVersionInfo(context) }
    val (simpleVersion, _) = remember { splitVersionAndChannel(versionName) }
    val normalizedChannelName = remember(versionName) { detectReleaseChannel(versionName) }

    val channelLabel = when {
        normalizedChannelName.equals("IntDev", ignoreCase = true) -> stringResource(R.string.about_dev_channel_value_intdev)
        normalizedChannelName.equals("Beta", ignoreCase = true) -> stringResource(R.string.about_dev_channel_value_beta)
        normalizedChannelName.equals("PreRelease", ignoreCase = true) -> stringResource(R.string.about_dev_channel_value_prerelease)
        normalizedChannelName.equals("Stable", ignoreCase = true) ||
            normalizedChannelName.equals("Release", ignoreCase = true) -> stringResource(R.string.about_dev_channel_value_stable)
        else -> stringResource(R.string.about_dev_channel_value_unknown)
    }
    val channelDescResId = when {
        normalizedChannelName.equals("IntDev", ignoreCase = true) -> R.string.about_dev_channel_desc_intdev
        normalizedChannelName.equals("Beta", ignoreCase = true) -> R.string.about_dev_channel_desc_dev
        normalizedChannelName.equals("PreRelease", ignoreCase = true) -> R.string.about_dev_channel_desc_prerelease
        normalizedChannelName.equals("Stable", ignoreCase = true) ||
            normalizedChannelName.equals("Release", ignoreCase = true) -> R.string.about_dev_channel_desc_stable
        else -> R.string.about_dev_channel_desc_unknown
    }

    var showChannelDialog by remember { mutableStateOf(false) }
    var showVersionDetailsDialog by remember { mutableStateOf(false) }
    var checkingUpdates by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

    fun startUpdateCheck() {
        if (checkingUpdates) return
        checkingUpdates = true
        updateStatus = resources.getString(R.string.about_update_checking)
        scope.launch {
            val result = onCheckForUpdates()
            checkingUpdates = false
            updateStatus = when(result) {
                is CheckOutcome.Available -> resources.getString(R.string.about_update_available,result.update.release.tag)
                is CheckOutcome.Failure, is CheckOutcome.Throttled -> resources.getString(R.string.goldy_update_failed)
                is CheckOutcome.Incomplete -> resources.getString(R.string.goldy_update_incomplete)
                else -> resources.getString(R.string.about_update_latest)
            }
        }
    }

    val expressive = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE
    val aboutScrollState = rememberScrollState()
    val pageContent: @Composable (PaddingValues) -> Unit = { padding ->
        AdaptiveContentPane(
            modifier = Modifier.padding(padding),
            maxWidth = FormContentMaxWidth
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(aboutScrollState)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // ── アプリ概要 ────────────────────────────────────────────
                AboutOverviewMosaic(
                    channelName = normalizedChannelName,
                    channelLabel = channelLabel,
                    versionName = versionName,
                    simpleVersion = simpleVersion,
                    versionCode = versionCode,
                    buildNumber = TrickuildMetadata.BUILD_NUMBER,
                    onVersionClick = { showVersionDetailsDialog = true },
                    onChannelClick = { showChannelDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                AboutCardsTheme {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.about_update_section_title),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold)
                        val canOpenUpdate = availableUpdate != null || hasSavedUpdate
                        AboutUpdateCard(
                            description = stringResource(R.string.about_update_section_help),
                            status = when {
                                hasSavedUpdate -> stringResource(R.string.about_update_saved)
                                availableUpdate != null -> stringResource(R.string.about_update_available, availableUpdate.release.tag)
                                else -> updateStatus ?: stringResource(R.string.goldy_update_idle)
                            },
                            actionLabel = stringResource(if (canOpenUpdate) R.string.goldy_update_view else R.string.about_update_check_button),
                            onCheck = {
                                if (hasSavedUpdate) onOpenUpdateCenter()
                                else availableUpdate?.let(onUpdateAvailable) ?: startUpdateCheck()
                            }, checking = checkingUpdates && !canOpenUpdate,
                            hasResult = updateStatus != null || canOpenUpdate
                        )
                    }
                    AboutActionGroup(
                        actions = listOf(
                            AboutAction("github", stringResource(R.string.about_support_github_title),
                                stringResource(R.string.about_support_help), stringResource(R.string.about_support_open_site)),
                            AboutAction("x", stringResource(R.string.about_support_x_title),
                                stringResource(R.string.about_support_x_help), stringResource(R.string.about_support_open_twitter)),
                            AboutAction("licenses", stringResource(R.string.about_oss_title),
                                stringResource(R.string.about_oss_help), stringResource(R.string.about_oss_open))
                        ),
                        onAction = { action ->
                            when (action.key) {
                                "github" -> openUrl(context, resources.getString(R.string.about_support_site_url))
                                "x" -> openUrl(context, resources.getString(R.string.about_support_twitter_url))
                                "licenses" -> onOssLicenses()
                            }
                        },
                        title = stringResource(R.string.about_information_title)
                    )
                }
            }
        }
    }
    if (expressive) {
        PyriteScreenScaffold(stringResource(R.string.about_screen_title), navigationIcon = {
            PyriteBackButton(onBack, stringResource(R.string.cd_back))
        }, content = pageContent)
    } else {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_screen_title)) },
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
        content = pageContent
    )
    }

    // チャネル説明ダイアログ
    if (showChannelDialog) {
        AlertDialog(
            onDismissRequest = { showChannelDialog = false },
            title = {
                Text(stringResource(R.string.about_dev_channel_dialog_title, channelLabel))
            },
            text = { Text(stringResource(channelDescResId)) },
            confirmButton = {
                TextButton(onClick = { showChannelDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    // バージョン詳細ダイアログ
    if (showVersionDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showVersionDetailsDialog = false },
            title = { Text(stringResource(R.string.about_version_details_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.about_version_details_message,
                        versionName,
                        versionCode,
                        TrickuildMetadata.BUILD_NUMBER
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { showVersionDetailsDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}

/** Give Pyrite cards the host's light/dark state without changing the standard M3 palette or type. */
@Composable
private fun AboutCardsTheme(content: @Composable () -> Unit) {
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        content()
        return
    }
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val motion = MaterialTheme.motionScheme
    PyriteTheme(darkTheme = colors.surface.luminance() < 0.5f, dynamicColor = false) {
        MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, motionScheme = motion,
            content = content)
    }
}

private fun resolveAppVersionInfo(context: Context): Pair<String, Int> {
    return try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val versionName = info.versionName ?: "unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
        Pair(versionName, versionCode)
    } catch (_: Exception) {
        Pair("unknown", 0)
    }
}

/** versionName から (coreVersion, channelName) に分割する。例: "1.2.0-Beta" → ("1.2.0", "Beta") */
private fun splitVersionAndChannel(versionName: String): Pair<String, String> {
    val hyphenPos = versionName.lastIndexOf('-')
    if (hyphenPos > 0 && hyphenPos < versionName.length - 1) {
        val core = versionName.substring(0, hyphenPos).trim()
        val channel = versionName.substring(hyphenPos + 1).trim().trim('(', ')')
        if (core.isNotBlank() && channel.isNotBlank()) return Pair(core, channel)
    }
    return Pair(versionName, "unknown")
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        // URLを開けない場合は無視
    }
}
