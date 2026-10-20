@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package jp.linkserver.nittcsc.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.linkserver.pyrite.settings.SettingsGroup
import jp.linkserver.pyrite.settings.SettingsContentCard
import jp.linkserver.pyrite.settings.SettingsSwitchItem
import jp.linkserver.pyrite.settings.SettingsNavigationItem
import jp.linkserver.pyrite.settings.SettingsSplitSwitchItem
import jp.linkserver.pyrite.settings.SettingsActionItem
import jp.linkserver.pyrite.screen.PyriteScreenScaffold
import jp.linkserver.pyrite.screen.PyriteBackButton
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.AdaptiveContentPane
import jp.linkserver.nittcsc.ui.ListContentMaxWidth
import jp.linkserver.nittcsc.ui.theme.LocalUiDesignMode

private val LocalInsideSettingsItem = staticCompositionLocalOf { false }

private enum class SettingsGroupPosition { Single, First, Middle, Last }

private fun settingsRowShape(position: SettingsGroupPosition, expressive: Boolean): Shape {
    val outerRadius = if (expressive) 22.dp else 20.dp
    val innerRadius = if (expressive) 6.dp else 4.dp
    return when (position) {
        SettingsGroupPosition.Single -> RoundedCornerShape(outerRadius)
        SettingsGroupPosition.First -> RoundedCornerShape(
            topStart = outerRadius, topEnd = outerRadius, bottomEnd = innerRadius, bottomStart = innerRadius
        )
        SettingsGroupPosition.Middle -> RoundedCornerShape(innerRadius)
        SettingsGroupPosition.Last -> RoundedCornerShape(
            topStart = innerRadius, topEnd = innerRadius, bottomEnd = outerRadius, bottomStart = outerRadius
        )
    }
}

private fun settingsRowPosition(index: Int, count: Int): SettingsGroupPosition = when {
    count <= 1 -> SettingsGroupPosition.Single
    index == 0 -> SettingsGroupPosition.First
    index == count - 1 -> SettingsGroupPosition.Last
    else -> SettingsGroupPosition.Middle
}

@Composable
private fun SettingsRowSurface(
    position: SettingsGroupPosition,
    modifier: Modifier = Modifier,
    expressiveStyle: Boolean = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE,
    content: @Composable () -> Unit
) {
    val expressive = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(
            min = if (expressive) 72.dp else 0.dp
        ),
        shape = settingsRowShape(position, expressiveStyle),
        color = if (expressiveStyle) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.CenterStart) {
            CompositionLocalProvider(LocalInsideSettingsItem provides true, content = content)
        }
    }
}

/** 1項目だけのPreference surface。複数項目にはAppSettingsGroupを使用する。 */
@Composable
fun PreferenceGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        SettingsContentCard(modifier) { Column(content = content) }
        return
    }

    SettingsRowSurface(SettingsGroupPosition.Single, modifier) {
        Column(content = content)
    }
}

@Composable
fun AppSettingsItem(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE && !LocalInsideSettingsItem.current) {
        SettingsContentCard(modifier) { Column(Modifier.padding(contentPadding)) { content() } }
        return
    }

    if (LocalInsideSettingsItem.current) {
        Column(modifier.padding(contentPadding)) { content() }
    } else {
        SettingsRowSurface(SettingsGroupPosition.Single, modifier) {
            Column(Modifier.padding(contentPadding)) { content() }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: AppSettingsGroupScope.() -> Unit) {
    val expressive = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE
    Column(verticalArrangement = Arrangement.spacedBy(if (expressive) 10.dp else 8.dp)) {
        AppSettingsCategory(title)
        AppSettingsGroup(content = content)
    }
}

class AppSettingsGroupScope internal constructor() {
    internal class Item(
        val key: String,
        val padding: PaddingValues,
        val content: @Composable () -> Unit,
        val standardOnly: Boolean = false,
        val children: List<Item>? = null
    )
    internal val items = mutableListOf<Item>()

    fun item(key: String, contentPadding: PaddingValues = PaddingValues(0.dp), content: @Composable () -> Unit) {
        items += Item(key, contentPadding, content)
    }

    fun standardOnly(key: String, content: @Composable () -> Unit) {
        items += Item(key, PaddingValues(0.dp), content, standardOnly = true)
    }

    fun section(
        sectionKey: String,
        standardContainer: @Composable (@Composable () -> Unit) -> Unit,
        content: AppSettingsGroupScope.() -> Unit
    ) {
        val children = AppSettingsGroupScope().apply(content).items
        items += Item(sectionKey, PaddingValues(0.dp), {
            standardContainer {
                children.forEach { child -> key(child.key) { child.content() } }
            }
        }, children = children)
    }
}

private fun List<AppSettingsGroupScope.Item>.expressiveItems(): List<AppSettingsGroupScope.Item> =
    flatMap { entry ->
        when {
            entry.standardOnly -> emptyList()
            entry.children != null -> entry.children.expressiveItems().map { child ->
                AppSettingsGroupScope.Item(
                    key = "${entry.key}/${child.key}",
                    padding = child.padding,
                    content = child.content
                )
            }
            else -> listOf(entry)
        }
    }

/** Expressiveは各行をつないだPreference群、通常M3は従来の入れ子構造で表示する。 */
@Composable
fun AppSettingsGroup(
    modifier: Modifier = Modifier,
    standardContentPadding: PaddingValues = PaddingValues(0.dp),
    standardSpacing: Dp = 0.dp,
    standardContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    connectedRows: Boolean = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE,
    expressiveStyle: Boolean = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE,
    content: AppSettingsGroupScope.() -> Unit
) {
    val entries = AppSettingsGroupScope().apply(content).items
    if (connectedRows && expressiveStyle && LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        val rows = entries.expressiveItems()
        SettingsGroup(modifier) {
            rows.forEach { entry ->
                item(entry.key) {
                    if (entry.padding != PaddingValues(0.dp)) {
                        SettingsContentCard { Column(Modifier.fillMaxWidth().padding(entry.padding)) { entry.content() } }
                    } else entry.content()
                }
            }
        }
        return
    }
    if (connectedRows) {
        val rows = entries.expressiveItems()
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEachIndexed { index, entry ->
                key(entry.key) {
                    SettingsRowSurface(settingsRowPosition(index, rows.size), expressiveStyle = expressiveStyle) {
                        Column(Modifier.fillMaxWidth().padding(entry.padding)) { entry.content() }
                    }
                }
            }
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            color = standardContainerColor) {
            Column(Modifier.padding(standardContentPadding),
                verticalArrangement = Arrangement.spacedBy(standardSpacing)) {
                entries.forEach { entry -> key(entry.key) { entry.content() } }
            }
        }
    }
}

@Composable
fun AppSettingsCategory(title: String, modifier: Modifier = Modifier) {
    val expressive = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE
    Text(
        text = title,
        modifier = modifier.semantics { heading() },
        style = if (expressive) MaterialTheme.typography.titleSmall.copy(
            fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        )
            else MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SettingsItemText(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (!summary.isNullOrEmpty()) Text(summary, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 参照アプリの配色を設定画面内だけに適用する。操作は親の行へ委譲する。 */
@Composable
private fun SettingsSwitch(checked: Boolean, enabled: Boolean = true) {
    AppSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
}

@Composable
fun AppSettingsSwitchItem(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        SettingsSwitchItem(title, checked, onCheckedChange, modifier, summary, enabled)
        return
    }
    AppSettingsItem {
        Row(modifier.fillMaxWidth().heightIn(min = 80.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SettingsItemText(title, summary, Modifier.weight(1f).alpha(if (enabled) 1f else .38f))
            SettingsSwitch(checked, enabled)
        }
    }
}

@Composable
fun AppSettingsNavigationItem(
    title: String,
    summary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        SettingsNavigationItem(title, onClick, modifier, summary)
        return
    }
    AppSettingsActionItem(title, summary, onClick, modifier) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 左側は詳細画面への移動、右側は有効・無効の切り替えとして個別に操作する。 */
@Composable
fun AppSettingsSwitchNavigationItem(
    title: String,
    summary: String?,
    checked: Boolean,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        SettingsSplitSwitchItem(title, checked, onCheckedChange, onClick, summary = summary)
        return
    }

    val horizontalPadding = 20.dp
    val verticalPadding = 20.dp
    AppSettingsItem {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                .heightIn(min = 80.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onClick,
                color = Color.Transparent,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsItemText(title, summary, Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            VerticalDivider(Modifier.height(32.dp))
            Box(
                modifier = Modifier.fillMaxHeight()
                    .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
                    .semantics { contentDescription = title }
                    .padding(horizontal = horizontalPadding),
                contentAlignment = Alignment.Center
            ) {
                SettingsSwitch(checked = checked)
            }
        }
    }
}

/** 展開操作をカテゴリ見出しから分離し、他の設定行と同じSurfaceで表示する。 */
@Composable
fun AppSettingsExpandableItem(
    title: String,
    summary: String?,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val state = stringResource(
        if (expanded) R.string.settings_expanded_state else R.string.settings_collapsed_state
    )
    AppSettingsActionItem(title, summary, onClick, Modifier.semantics { stateDescription = state }) {
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AppSettingsActionItem(
    title: String,
    summary: String?,
    onClick: () -> Unit,
    modifier: Modifier,
    trailingContent: @Composable () -> Unit
) {
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        SettingsActionItem(title, onClick, modifier, summary, trailingContent = trailingContent)
        return
    }
    AppSettingsItem {
        Surface(onClick = onClick, color = Color.Transparent, modifier = modifier.fillMaxWidth()) {
            Row(Modifier.heightIn(min = 80.dp).padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SettingsItemText(title, summary, Modifier.weight(1f))
                trailingContent()
            }
        }
    }
}

/** 値は要約として表示し、編集UIはタップしたときだけ開く。 */
@Composable
fun ValuePreferenceRow(title: String, value: String, onClick: () -> Unit, summary: String? = null) {
    PreferenceRow(title = title, summary = listOfNotNull(value.takeIf { it.isNotBlank() }, summary).joinToString("\n"), onClick = onClick)
}

@Composable
fun PreferenceRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    trailingContent: @Composable () -> Unit = {}
) {
    AppSettingsActionItem(title, summary, onClick, modifier, trailingContent)
}

@Composable
fun SwitchPreferenceRow(
    title: String, summary: String?, checked: Boolean,
    onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true
) = AppSettingsSwitchItem(title, summary, checked, onCheckedChange, enabled = enabled)

@Composable
fun NavigationPreferenceRow(title: String, summary: String?, onClick: () -> Unit) =
    AppSettingsNavigationItem(title, summary, onClick)

/** Expressiveは固定ツールバー、通常M3は従来の可変ツールバーを使う。 */
@Composable
internal fun AppSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    scrollState: ScrollState,
    scrollEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        PyriteScreenScaffold(title, navigationIcon = {
            PyriteBackButton(onBack, stringResource(R.string.cd_back))
        }) { padding ->
            AdaptiveContentPane(Modifier.padding(padding), maxWidth = ListContentMaxWidth) {
                Column(Modifier.fillMaxSize().verticalScroll(scrollState, enabled = scrollEnabled)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp), content = content)
            }
        }
        return
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        AppFlexibleTopAppBar(title = { Text(title) }, navigationIcon = {
            AppIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
            }
        })
    }) { padding ->
        AdaptiveContentPane(Modifier.padding(padding), maxWidth = ListContentMaxWidth) {
            Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState, enabled = scrollEnabled),
                verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
        }
    }
}
