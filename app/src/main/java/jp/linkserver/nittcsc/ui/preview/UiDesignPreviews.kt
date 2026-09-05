@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package jp.linkserver.nittcsc.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.SettingsScreen
import jp.linkserver.nittcsc.ui.components.AppBottomNavigation
import jp.linkserver.nittcsc.ui.components.AppBottomNavigationItem
import jp.linkserver.nittcsc.ui.components.AppButtonGroup
import jp.linkserver.nittcsc.ui.components.AppCard
import jp.linkserver.nittcsc.ui.components.AppFabMenu
import jp.linkserver.nittcsc.ui.components.AppFabMenuAction
import jp.linkserver.nittcsc.ui.components.AppFlexibleTopAppBar
import jp.linkserver.nittcsc.ui.components.AppFloatingToolbar
import jp.linkserver.nittcsc.ui.components.AppIconButton
import jp.linkserver.nittcsc.ui.components.AppListItem
import jp.linkserver.nittcsc.ui.components.AppLoadingIndicator
import jp.linkserver.nittcsc.ui.components.AppPrimaryButton
import jp.linkserver.nittcsc.ui.components.AppProgressIndicator
import jp.linkserver.nittcsc.ui.components.AppSearchBar
import jp.linkserver.nittcsc.ui.components.AppSecondaryButton
import jp.linkserver.nittcsc.ui.components.AppSplitButton
import jp.linkserver.nittcsc.ui.components.AppTopAppBar
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import java.time.LocalDate

@Preview(name = "Components · M3 Light", group = "Design system", showBackground = true)
@Composable
private fun ComponentsMaterial3LightPreview() {
    ComponentsPreview(UiDesignMode.MATERIAL_3, darkTheme = false)
}

@Preview(name = "Components · M3 Dark", group = "Design system", showBackground = true)
@Composable
private fun ComponentsMaterial3DarkPreview() {
    ComponentsPreview(UiDesignMode.MATERIAL_3, darkTheme = true)
}

@Preview(name = "Components · M3E Light", group = "Design system", showBackground = true)
@Composable
private fun ComponentsExpressiveLightPreview() {
    ComponentsPreview(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = false)
}

@Preview(name = "Components · M3E Dark", group = "Design system", showBackground = true)
@Composable
private fun ComponentsExpressiveDarkPreview() {
    ComponentsPreview(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = true)
}

@Preview(name = "Advanced · M3E Light", group = "Design system", showBackground = true)
@Composable
private fun AdvancedExpressiveLightPreview() {
    AdvancedComponentsPreview(darkTheme = false)
}

@Preview(name = "Advanced · M3E Dark", group = "Design system", showBackground = true)
@Composable
private fun AdvancedExpressiveDarkPreview() {
    AdvancedComponentsPreview(darkTheme = true)
}

@Composable
private fun AdvancedComponentsPreview(darkTheme: Boolean) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    AppTheme(
        uiDesignMode = UiDesignMode.MATERIAL_3_EXPRESSIVE,
        darkTheme = darkTheme,
        dynamicColor = false
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppFlexibleTopAppBar(title = { Text("課題と予定") })
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppButtonGroup(
                        options = listOf("課題", "予定"),
                        selectedIndex = selectedIndex,
                        onSelectedIndexChange = { selectedIndex = it }
                    )
                    AppFloatingToolbar(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text("前へ")
                            Text("今日")
                            Text("次へ")
                        }
                    }
                    AppSplitButton(
                        primaryLabel = { Text("課題を追加") },
                        onPrimaryClick = {},
                        secondaryLabel = { Text("予定を追加") },
                        onSecondaryClick = {}
                    )
                }
                AppFabMenu(
                    actions = listOf(
                        AppFabMenuAction("課題を追加", Icons.Filled.Add, onClick = {}),
                        AppFabMenuAction("予定を追加", Icons.Filled.Edit, onClick = {})
                    ),
                    expanded = true,
                    onExpandedChange = {},
                    expandContentDescription = "追加メニューを開く",
                    collapseContentDescription = "追加メニューを閉じる",
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}

@Composable
private fun ComponentsPreview(mode: UiDesignMode, darkTheme: Boolean) {
    AppTheme(uiDesignMode = mode, darkTheme = darkTheme, dynamicColor = false) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopAppBar(
                title = { Text("NITTC Scheduler") },
                actions = {
                    AppIconButton(onClick = {}) {
                        Icon(Icons.Filled.Settings, contentDescription = "設定")
                    }
                }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppPrimaryButton(onClick = {}) { Text("保存") }
                    AppSecondaryButton(onClick = {}) { Text("キャンセル") }
                }
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    AppListItem(
                        headlineContent = { Text("デザイン") },
                        supportingContent = { Text(mode.name) },
                        trailingContent = {
                            Icon(Icons.Filled.Check, contentDescription = "選択中")
                        }
                    )
                }
                AppSearchBar(
                    query = "",
                    onQueryChange = {},
                    onSearch = {},
                    placeholder = "課題を検索",
                    modifier = Modifier.fillMaxWidth()
                )
                AppProgressIndicator(progress = 0.65f, modifier = Modifier.fillMaxWidth())
                AppLoadingIndicator(modifier = Modifier.size(40.dp))
            }
            AppBottomNavigation {
                AppBottomNavigationItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Filled.Home, contentDescription = "ホーム") },
                    label = { Text("ホーム") }
                )
                AppBottomNavigationItem(
                    selected = false,
                    onClick = {},
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "設定") },
                    label = { Text("設定") }
                )
            }
        }
    }
}

@Preview(name = "Settings · M3 Light", group = "Settings", showBackground = true)
@Composable
private fun SettingsMaterial3LightPreview() {
    SettingsPreview(UiDesignMode.MATERIAL_3, darkTheme = false)
}

@Preview(name = "Settings · M3 Dark", group = "Settings", showBackground = true)
@Composable
private fun SettingsMaterial3DarkPreview() {
    SettingsPreview(UiDesignMode.MATERIAL_3, darkTheme = true)
}

@Preview(name = "Settings · M3E Light", group = "Settings", showBackground = true)
@Composable
private fun SettingsExpressiveLightPreview() {
    SettingsPreview(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = false)
}

@Preview(name = "Settings · M3E Dark", group = "Settings", showBackground = true)
@Composable
private fun SettingsExpressiveDarkPreview() {
    SettingsPreview(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = true)
}

@Composable
private fun SettingsPreview(mode: UiDesignMode, darkTheme: Boolean) {
    val settings = SettingsEntity(
        termStart = LocalDate.of(2026, 4, 1),
        termEnd = LocalDate.of(2027, 3, 31)
    )
    AppTheme(uiDesignMode = mode, darkTheme = darkTheme, dynamicColor = false) {
        SettingsScreen(
            state = SchedulerUiState(
                settings = settings,
                uiDesignMode = mode,
                expressiveWarningAcknowledged = true
            ),
            onBack = {},
            onAbout = {},
            onToggleLocalAi = {},
            onToggleDrawerNavigation = {},
            onToggleAddTasksToCalendar = {},
            onToggleCurrentTimeMarker = {},
            onToggleUnifyTaskPlanView = {},
            onToggleShowWeekdayOnDates = {},
            onToggleAdvancedTimeSettingsUi = {}
        )
    }
}
