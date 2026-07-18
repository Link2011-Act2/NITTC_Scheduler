package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun AdaptiveAppNavigationRail(
    selectedTab: AppTab,
    unifyTaskPlanView: Boolean,
    selectedIndicatorColor: Color,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(modifier = modifier.fillMaxHeight()) {
        AppTab.entries.forEach { tab ->
            if (unifyTaskPlanView && tab == AppTab.Plans) return@forEach

            val isSelected = selectedTab == tab
            val tabLabel = if (unifyTaskPlanView && tab == AppTab.Tasks) {
                "ToDo"
            } else {
                stringResource(tab.labelRes)
            }
            NavigationRailItem(
                selected = isSelected,
                onClick = { onSelectTab(tab) },
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = selectedIndicatorColor
                ),
                icon = {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tabLabel,
                        modifier = if (!isSelected && tab == AppTab.Plans) {
                            Modifier.offset(x = (-0.5).dp)
                        } else {
                            Modifier
                        }
                    )
                },
                label = { androidx.compose.material3.Text(tabLabel) },
                alwaysShowLabel = true
            )
        }
    }
}
