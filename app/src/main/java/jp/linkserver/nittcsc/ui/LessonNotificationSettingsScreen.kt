package jp.linkserver.nittcsc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.ui.components.AppSettingsGroup
import jp.linkserver.nittcsc.ui.components.AppSettingsScaffold
import jp.linkserver.nittcsc.ui.components.AppSettingsSwitchNavigationItem

@Composable
internal fun LessonStartNotificationSettingsEntry(
    enabled: Boolean,
    onOpenDetails: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    AppSettingsGroup {
        item("lesson-start-notifications") {
            AppSettingsSwitchNavigationItem(
                title = stringResource(R.string.label_lesson_start_notification),
                summary = stringResource(R.string.desc_lesson_start_notification),
                checked = enabled,
                onClick = onOpenDetails,
                onCheckedChange = onToggleEnabled
            )
        }
    }
}

@Composable
internal fun LessonStartNotificationSettingsScreen(
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    BackHandler(onBack = onBack)
    AppSettingsScaffold(
        title = stringResource(R.string.label_lesson_start_notification),
        onBack = onBack,
        scrollState = rememberScrollState()
    ) {
        content()
    }
}
