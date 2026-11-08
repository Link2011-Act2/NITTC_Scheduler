package jp.linkserver.nittcsc.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.update.*
import jp.linkserver.goldy.compose.UpdateOverviewScreen as EliefOverviewScreen

@Composable
fun UpdateOverviewScreen(updateInfo: UpdateCandidate?, onBack: () -> Unit) {
    val model: UpdateDownloadViewModel = viewModel()
    val state by model.state.collectAsState()
    val pinned by model.selection.collectAsState()
    val candidate = pinned ?: updateInfo
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(updateInfo) { model.selectCandidate(updateInfo) }
    DisposableEffect(owner,model) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_RESUME) model.onForegroundChanged(true)
            if(event==Lifecycle.Event.ON_PAUSE) model.onForegroundChanged(false)
        }
        owner.lifecycle.addObserver(observer)
        model.onForegroundChanged(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { owner.lifecycle.removeObserver(observer); model.onForegroundChanged(false) }
    }
    var releaseConfirmation by remember { mutableStateOf<InstallRequest?>(null) }
    var reopenConfirmation by remember { mutableStateOf<InstallRequest?>(null) }
    releaseConfirmation?.let { request ->
        AlertDialog(onDismissRequest={releaseConfirmation=null},
            title={Text(stringResource(R.string.goldy_update_release_title))},
            text={Text(stringResource(R.string.goldy_update_release_body))},
            confirmButton={TextButton(onClick={releaseConfirmation=null; model.release(request)}) { Text(stringResource(R.string.goldy_update_release_confirm)) }},
            dismissButton={TextButton(onClick={releaseConfirmation=null}) { Text(stringResource(android.R.string.cancel)) }})
    }
    reopenConfirmation?.let { request ->
        AlertDialog(onDismissRequest={reopenConfirmation=null},
            title={Text(stringResource(R.string.goldy_update_reopen))},
            text={Text(stringResource(R.string.goldy_update_reopen_body))},
            confirmButton={TextButton(onClick={reopenConfirmation=null; model.reopen(request)}) { Text(stringResource(R.string.goldy_transfer_install)) }},
            dismissButton={TextButton(onClick={reopenConfirmation=null}) { Text(stringResource(android.R.string.cancel)) }})
    }
    val cardStyle=LocalUpdateCardStyle.current
    val active=state.releaseIdentity==transferIdentity(candidate)
    val strings=goldyUpdateStrings().let { if(active && state.waiting) it.copy(download=stringResource(R.string.goldy_transfer_restart)) else it }
    EliefOverviewScreen(UpdateUiState(candidate,apkAvailable=candidate?.release?.let(ApkUpdateProfile.selection::select)!=null,
        transfer=if(state.restoring) UpdateTransferState.PreparingInstall else if(active) state.transfer else UpdateTransferState.Idle,
        message=state.message.takeUnless { state.transfer is UpdateTransferState.Failed }),onBack,
        { openUpdateUrl(context,it) },{ model.action(candidate,it) },strings=strings,header={
            if(active && state.waiting) {
                state.retainedProgress?.let { Text(DownloadProgressFormatter.format(it,locale)) }
                TextButton(onClick=model::cancel) { Text(stringResource(R.string.goldy_update_cancel_download)) }
            }
            if(state.requests.isNotEmpty()) Text(stringResource(R.string.goldy_update_retained))
            state.requests.forEach { request ->
                Surface(color=cardStyle.containerColor,shape=cardStyle.shape ?: MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp)) {
                        Text(request.metadata.versionName)
                        Text(stringResource(R.string.goldy_update_release_explanation))
                        TextButton(onClick={reopenConfirmation=request}) { Text(stringResource(R.string.goldy_update_reopen)) }
                        TextButton(onClick={releaseConfirmation=request}) { Text(stringResource(R.string.goldy_update_release_request)) }
                    }
                }
            }
        })
}
internal fun openUpdateUrl(context: android.content.Context, url: String) {
    val uri=android.net.Uri.parse(url)
    if(uri.scheme in listOf("https","http")) runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,uri)) }
}
