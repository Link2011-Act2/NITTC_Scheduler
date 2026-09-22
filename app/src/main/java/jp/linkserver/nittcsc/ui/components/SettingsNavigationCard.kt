package jp.linkserver.nittcsc.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun SettingsNavigationCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppSettingsNavigationItem(title, description, onClick, modifier)
}
