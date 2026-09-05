@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package jp.linkserver.nittcsc.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.AdaptiveContentPane
import jp.linkserver.nittcsc.ui.ListContentMaxWidth
import jp.linkserver.nittcsc.ui.theme.LocalUiDesignMode

enum class SettingsGroupPosition { Single, Top, Middle, Bottom }

private val LocalInsideSettingsItem = staticCompositionLocalOf { false }

/** 設定行の形状は補正せず、公式のconnected item tokenをそのまま使用する。 */
@Composable
private fun settingsItemShape(position: SettingsGroupPosition): Shape =
    when (position) {
        SettingsGroupPosition.Single -> MenuDefaults.standaloneItemShape
        SettingsGroupPosition.Top -> MenuDefaults.leadingItemShape
        SettingsGroupPosition.Middle -> MenuDefaults.middleItemShape
        SettingsGroupPosition.Bottom -> MenuDefaults.trailingItemShape
    }

/** 各行を別Surfaceにする。グループの中から使う場合はSurfaceを重ねない。 */
@Composable
fun AppSettingsItem(
    modifier: Modifier = Modifier,
    position: SettingsGroupPosition = SettingsGroupPosition.Single,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {
    if (LocalInsideSettingsItem.current) {
        content()
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = settingsItemShape(position),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            CompositionLocalProvider(LocalInsideSettingsItem provides true) {
                Column(
                    Modifier.padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) { content() }
            }
        }
    }
}

class AppSettingsGroupScope internal constructor() {
    internal class Item(
        val key: String,
        val padding: PaddingValues,
        val standardOnly: Boolean,
        val content: @Composable () -> Unit,
        val children: List<Item>? = null
    )

    internal val items = mutableListOf<Item>()

    fun item(
        key: String,
        contentPadding: PaddingValues = PaddingValues(0.dp),
        content: @Composable () -> Unit
    ) {
        items += Item(key, contentPadding, false, content)
    }

    /** 通常M3にある区切り線などを、Expressiveの項目数に含めず維持する。 */
    fun standardOnly(key: String, content: @Composable () -> Unit) {
        items += Item(key, PaddingValues(0.dp), true, content)
    }

    /** 通常M3の入れ子レイアウトを保ち、Expressiveでは同じ行を平らなグループにする。 */
    fun section(
        key: String,
        standardContainer: @Composable (@Composable () -> Unit) -> Unit,
        content: AppSettingsGroupScope.() -> Unit
    ) {
        val children = AppSettingsGroupScope().apply(content).items
        items += Item(key, PaddingValues(0.dp), false, {
            standardContainer {
                children.forEach { entry -> key(entry.key) { entry.content() } }
            }
        }, children)
    }
}

private fun List<AppSettingsGroupScope.Item>.expressiveItems(): List<AppSettingsGroupScope.Item> =
    flatMap { entry ->
        when {
            entry.standardOnly -> emptyList()
            entry.children != null -> entry.children.expressiveItems().map { child ->
                AppSettingsGroupScope.Item("${entry.key}/${child.key}", child.padding, false, child.content)
            }
            else -> listOf(entry)
        }
    }

/** 条件付きの行も実際に表示する件数から角丸を決め、保存状態は呼び出し元で管理する。 */
@Composable
fun AppSettingsGroup(
    modifier: Modifier = Modifier,
    standardContentPadding: PaddingValues = PaddingValues(0.dp),
    standardSpacing: Dp = 0.dp,
    standardContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: AppSettingsGroupScope.() -> Unit
) {
    val expressive = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE
    val items = AppSettingsGroupScope().apply(content).items
    val entries = if (expressive) items.expressiveItems() else items
    if (expressive) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            entries.forEachIndexed { index, entry ->
                key(entry.key) {
                    // 入れ子のグループにも独立した行のSurfaceを作る。
                    CompositionLocalProvider(LocalInsideSettingsItem provides false) {
                        AppSettingsItem(
                            position = when {
                                entries.size == 1 -> SettingsGroupPosition.Single
                                index == 0 -> SettingsGroupPosition.Top
                                index == entries.lastIndex -> SettingsGroupPosition.Bottom
                                else -> SettingsGroupPosition.Middle
                            },
                            contentPadding = entry.padding,
                            content = entry.content
                        )
                    }
                }
            }
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = standardContainerColor
        ) {
            Column(
                Modifier.padding(standardContentPadding),
                verticalArrangement = Arrangement.spacedBy(standardSpacing)
            ) {
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
        modifier = modifier
            .then(if (expressive) Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp) else Modifier)
            .semantics { heading() },
        style = if (expressive) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SettingsItemText(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
        if (!summary.isNullOrEmpty()) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
    AppSettingsItem {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingsItemText(title, summary, Modifier.weight(1f))
            // 行全体を1つのSwitchとして読み上げ、thumbも同じクリック領域で操作する。
            AppSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
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
    AppSettingsActionItem(title, summary, onClick, modifier) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    AppSettingsItem {
        Surface(onClick = onClick, color = Color.Transparent, modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.heightIn(min = 80.dp).padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SettingsItemText(title, summary, Modifier.weight(1f))
                trailingContent()
            }
        }
    }
}

/** 数値などの編集欄を末尾に置く設定行。テキストには残り幅を割り当てて折り返す。 */
@Composable
fun AppSettingsValueItem(
    title: String,
    summary: String?,
    trailingContent: @Composable () -> Unit
) {
    AppSettingsItem {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsItemText(title, summary, Modifier.weight(1f))
            trailingContent()
        }
    }
}

/** タイトルはスクロール本文に置き、丸い戻るボタンだけを上部に残す。 */
@Composable
internal fun AppSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    scrollState: ScrollState,
    scrollEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val expressive = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE
    Scaffold(
        topBar = {
            if (!expressive) {
                AppFlexibleTopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        AppIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                        }
                    }
                )
            }
        }
    ) { padding ->
        AdaptiveContentPane(modifier = Modifier.padding(padding), maxWidth = ListContentMaxWidth) {
            if (expressive) {
                Column(Modifier.fillMaxSize()) {
                    FilledIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 12.dp).size(48.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f)
                            .verticalScroll(scrollState, enabled = scrollEnabled)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(36.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            Spacer(Modifier.height(72.dp))
                            Text(
                                title,
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.semantics { heading() }
                            )
                            Spacer(Modifier.height(36.dp))
                        }
                        content()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                        .verticalScroll(scrollState, enabled = scrollEnabled),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    content = content
                )
            }
        }
    }
}
