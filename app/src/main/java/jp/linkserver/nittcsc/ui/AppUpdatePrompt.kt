package jp.linkserver.nittcsc.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.ui.components.AppIconButton
import jp.linkserver.nittcsc.update.AppUpdateInfo

@Composable
internal fun AppUpdateAction(onClick: () -> Unit) {
    AppIconButton(onClick = onClick) {
        BadgedBox(badge = { Badge() }) {
            Icon(
                painter = painterResource(R.drawable.browser_updated),
                contentDescription = stringResource(R.string.cd_available_app_update),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun AppUpdatePrompt(
    update: AppUpdateInfo,
    onConfirm: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.browser_updated),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.update_screen_title)) },
        text = { Text(stringResource(R.string.update_prompt_body, update.tagName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.update_prompt_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onIgnore) {
                Text(stringResource(R.string.update_prompt_ignore))
            }
        }
    )
}
