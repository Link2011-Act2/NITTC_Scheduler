package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.ui.components.AppIconButton
import jp.linkserver.nittcsc.ui.components.AppPrimaryButton
import jp.linkserver.nittcsc.ui.components.AppSecondaryButton
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppPrimaryButton(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.update_prompt_confirm))
                }
                AppSecondaryButton(
                    onClick = onIgnore,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.update_prompt_ignore))
                }
            }
        }
    )
}
