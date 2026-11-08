package jp.linkserver.nittcsc.ui

import androidx.compose.runtime.Composable
import jp.linkserver.goldy.GoldyState
import jp.linkserver.goldy.compose.UpdateCheckDialog
import jp.linkserver.goldy.compose.UpdateNotificationIcon

@Composable
internal fun AppUpdateAction(state: GoldyState, onClick: () -> Unit) =
    UpdateNotificationIcon(state, { onClick() }, strings=goldyNotificationStrings())

@Composable
internal fun AppUpdatePrompt(state: GoldyState, onCheck: () -> Unit, onConfirm: () -> Unit,
    onIgnore: (String) -> Unit, onDismiss: () -> Unit) = UpdateCheckDialog(state,onCheck,
    { onConfirm() },onIgnore,onDismiss,strings=goldyNotificationStrings())
