@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package jp.linkserver.nittcsc.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.ui.AdaptiveContentPane
import jp.linkserver.nittcsc.ui.ListContentMaxWidth

private val LocalInsideSettingsItem = staticCompositionLocalOf { false }

/** カテゴリ全体で背景と角丸を共有し、行のクリック領域は独立させる。 */
@Composable
fun PreferenceGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceBright
    ) {
        CompositionLocalProvider(LocalInsideSettingsItem provides true) {
            Column(content = content)
        }
    }
}

@Composable
fun AppSettingsItem(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {
    if (LocalInsideSettingsItem.current) {
        Column(modifier.padding(contentPadding)) { content() }
    } else {
        PreferenceGroup(modifier) {
            Column(Modifier.padding(contentPadding)) { content() }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSettingsCategory(title)
        PreferenceGroup(content = content)
    }
}

class AppSettingsGroupScope internal constructor() {
    internal class Item(val key: String, val padding: PaddingValues, val content: @Composable () -> Unit)
    internal val items = mutableListOf<Item>()

    fun item(key: String, contentPadding: PaddingValues = PaddingValues(0.dp), content: @Composable () -> Unit) {
        items += Item(key, contentPadding, content)
    }
}

/** 条件付きの行も安定したキーで保持し、カテゴリのSurface内に連続表示する。 */
@Composable
fun AppSettingsGroup(modifier: Modifier = Modifier, content: AppSettingsGroupScope.() -> Unit) {
    val entries = AppSettingsGroupScope().apply(content).items
    PreferenceGroup(modifier) {
        entries.forEach { entry ->
            key(entry.key) {
                Column(Modifier.fillMaxWidth().padding(entry.padding)) { entry.content() }
            }
        }
    }
}

@Composable
fun AppSettingsCategory(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.padding(horizontal = 16.dp).semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun SettingsItemText(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal)
        if (!summary.isNullOrEmpty()) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
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
                .heightIn(min = 72.dp)
                .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsItemText(title, summary, Modifier.weight(1f).alpha(if (enabled) 1f else 0.38f))
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
                modifier = Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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

/** 通常サイズの固定ツールバー。システムバーのinsetはScaffoldで処理する。 */
@Composable
internal fun AppSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    scrollState: ScrollState,
    scrollEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        AdaptiveContentPane(modifier = Modifier.padding(padding), maxWidth = ListContentMaxWidth) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(scrollState, enabled = scrollEnabled)
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                content = content
            )
        }
    }
}
