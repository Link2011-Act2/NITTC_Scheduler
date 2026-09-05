@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package jp.linkserver.nittcsc.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SplitButton
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.ExpressiveFabShape
import jp.linkserver.nittcsc.ui.theme.LocalUiDesignMode

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val useExpressiveIcons = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = if (useExpressiveIcons) {
            {
                Icon(
                    imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        } else {
            null
        }
    )
}

@Composable
fun AppPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val expressive = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = if (expressive) MaterialTheme.shapes.large else ButtonDefaults.shape,
        content = content
    )
}

@Composable
fun AppSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val expressive = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = if (expressive) MaterialTheme.shapes.large else ButtonDefaults.outlinedShape,
        content = content
    )
}

@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            content = content
        )
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content
        )
    }
}

@Composable
fun AppTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions
    )
}

/**
 * 通常M3では既存のTopAppBarを維持し、M3Eではスクロールに追従するFlexible App Barへ切り替える。
 */
@Composable
fun AppFlexibleTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        MediumFlexibleTopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior
        )
    } else {
        TopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions
        )
    }
}

@Composable
fun AppBottomNavigation(
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: @Composable AppBottomNavigationScope.() -> Unit
) {
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                toolbarContentColor = MaterialTheme.colorScheme.onSurface
            )
            val retainedFab = remember { RetainedFloatingActionButton() }
            if (floatingActionButton != null) {
                retainedFab.content = floatingActionButton
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalFloatingToolbar(
                    expanded = true,
                    modifier = Modifier.width(316.dp),
                    colors = colors,
                    shape = FloatingToolbarDefaults.ContainerShape,
                    expandedShadowElevation = FloatingToolbarDefaults.ContainerExpandedElevation,
                    collapsedShadowElevation = FloatingToolbarDefaults.ContainerCollapsedElevation
                ) {
                    ExpressiveBottomNavigationScope.content()
                }
                AnimatedVisibility(
                    visible = floatingActionButton != null,
                    enter = expandHorizontally(
                        expandFrom = Alignment.Start,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                    ) + fadeIn(
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
                    ) + scaleIn(
                        initialScale = 0.8f,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                    ),
                    exit = shrinkHorizontally(
                        shrinkTowards = Alignment.Start,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                    ) + fadeOut(
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
                    ) + scaleOut(
                        targetScale = 0.8f,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                    ),
                    label = "AttachedVibrantFabVisibility"
                ) {
                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        retainedFab.content?.invoke()
                    }
                }
            }
        }
    } else {
        NavigationBar(modifier = modifier) {
            StandardBottomNavigationScope(this).content()
        }
    }
}

interface AppBottomNavigationScope

private data class StandardBottomNavigationScope(
    val rowScope: RowScope
) : AppBottomNavigationScope

private object ExpressiveBottomNavigationScope : AppBottomNavigationScope

private class RetainedFloatingActionButton {
    var content: (@Composable () -> Unit)? = null
}

@Composable
fun AppBottomNavigationScope.AppBottomNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable (() -> Unit)?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedIndicatorColor: Color = MaterialTheme.colorScheme.secondaryContainer
) {
    if (this is ExpressiveBottomNavigationScope) {
        val containerColor = if (selected) selectedIndicatorColor else Color.Transparent
        val contentColor = if (selected) {
            contentColorFor(selectedIndicatorColor)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Surface(
            modifier = modifier
                .height(48.dp)
                .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.Tab,
                    onClick = onClick
                ),
            shape = MaterialTheme.shapes.extraLarge,
            color = containerColor,
            contentColor = contentColor
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = if (selected && label != null) 14.dp else 12.dp)
                    .then(
                        if (contentDescription != null) {
                            Modifier.clearAndSetSemantics {
                                this.contentDescription = contentDescription
                            }
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
                if (selected && label != null) {
                    Spacer(Modifier.width(8.dp))
                    label()
                }
            }
        }
    } else if (this is StandardBottomNavigationScope) {
        with(rowScope) {
            NavigationBarItem(
                selected = selected,
                onClick = onClick,
                icon = icon,
                label = label,
                modifier = modifier,
                enabled = enabled,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = selectedIndicatorColor
                )
            )
        }
    }
}

@Composable
fun AppFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val expressive = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = if (expressive) ExpressiveFabShape else FloatingActionButtonDefaults.shape,
        containerColor = if (expressive) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            FloatingActionButtonDefaults.containerColor
        },
        content = content
    )
}

data class AppFabMenuAction(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String = label,
    val onClick: () -> Unit
)

/** フローティングツールバーへ接続する、強調色の作成メニューFAB。 */
@Composable
fun AppVibrantFabMenu(
    actions: List<AppFabMenuAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    expandContentDescription: String,
    collapseContentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        FloatingToolbarDefaults.VibrantFloatingActionButton(
            onClick = { onExpandedChange(!expanded) }
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = if (expanded) {
                    collapseContentDescription
                } else {
                    expandContentDescription
                }
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            actions.forEach { action ->
                Surface(
                    onClick = {
                        onExpandedChange(false)
                        action.onClick()
                    },
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(action.icon, contentDescription = null)
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

/** M3E用の展開型FAB。各項目は1タップで実行し、選択後に自動で閉じる。 */
@Composable
fun AppFabMenu(
    actions: List<AppFabMenuAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    expandContentDescription: String,
    collapseContentDescription: String,
    modifier: Modifier = Modifier
) {
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = if (expanded) {
                        collapseContentDescription
                    } else {
                        expandContentDescription
                    }
                )
            }
        },
        modifier = modifier
    ) {
        actions.forEach { action ->
            FloatingActionButtonMenuItem(
                onClick = {
                    onExpandedChange(false)
                    action.onClick()
                },
                text = { Text(action.label) },
                icon = {
                    Icon(action.icon, contentDescription = action.contentDescription)
                }
            )
        }
    }
}

/** 通常M3では従来Card、M3Eでは画面上に浮くツールバー表現を使う。 */
@Composable
fun AppFloatingToolbar(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = modifier,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = containerColor,
                toolbarContentColor = contentColorFor(containerColor)
            )
        ) { content() }
    } else {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) { content() }
    }
}

/** 通常M3のタブ列を、M3Eではまとまりを強調した単一選択Button Groupにする。 */
@Composable
fun AppButtonGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = selectedIndex == index,
                    onClick = { onSelectedIndexChange(index) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(option) }
                )
            }
        }
    } else {
        PrimaryTabRow(selectedTabIndex = selectedIndex, modifier = modifier) {
            options.forEachIndexed { index, option ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { onSelectedIndexChange(index) },
                    text = { Text(option) }
                )
            }
        }
    }
}

/** M3EのConnected button groupを使う単一選択ボタン列。 */
@Composable
fun AppConnectedButtonGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, option ->
            val selected = selectedIndex == index
            ToggleButton(
                checked = selected,
                onCheckedChange = { onSelectedIndexChange(index) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton }
            ) {
                AnimatedVisibility(
                    visible = selected,
                    enter = expandHorizontally(
                        expandFrom = Alignment.CenterHorizontally,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                    ) + fadeIn(
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()
                    ) + scaleIn(
                        initialScale = 0.6f,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                    ),
                    exit = shrinkHorizontally(
                        shrinkTowards = Alignment.CenterHorizontally,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                    ) + fadeOut(
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()
                    ) + scaleOut(
                        targetScale = 0.6f,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                    ),
                    label = "ConnectedButtonCheckVisibility"
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ToggleButtonDefaults.IconSpacing))
                    }
                }
                Text(option, maxLines = 1)
            }
        }
    }
}

/** 2つの対等な即時操作を、M3Eの連結したSplit Buttonとして表示する。 */
@Composable
fun AppSplitButton(
    primaryLabel: @Composable RowScope.() -> Unit,
    onPrimaryClick: () -> Unit,
    secondaryLabel: @Composable RowScope.() -> Unit,
    onSecondaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true
) {
    SplitButton(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onPrimaryClick,
                enabled = primaryEnabled,
                content = primaryLabel
            )
        },
        trailingButton = {
            SplitButtonDefaults.OutlinedTrailingButton(
                checked = false,
                onCheckedChange = { onSecondaryClick() },
                enabled = secondaryEnabled,
                content = secondaryLabel
            )
        },
        modifier = modifier
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val expressive = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE
    val clickableModifier = if (onClick != null) {
        modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = clickableModifier,
        shape = if (expressive) MaterialTheme.shapes.large else MaterialTheme.shapes.medium,
        color = containerColor,
        tonalElevation = if (expressive) 1.dp else 0.dp,
        content = content
    )
}

@Composable
fun AppListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val itemModifier = if (onClick != null) {
        modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        modifier
    }
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
        Surface(shape = MaterialTheme.shapes.medium, color = Color.Transparent) {
            ListItem(
                modifier = itemModifier,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { headlineContent() }
        }
    } else {
        ListItem(
            modifier = itemModifier,
            supportingContent = supportingContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        ) { headlineContent() }
    }
}

@Composable
fun AppLoadingIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    compact: Boolean = false
) {
    val expressive = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE
    if (expressive) {
        if (progress == null) {
            LoadingIndicator(modifier = modifier)
        } else {
            LoadingIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = modifier
            )
        }
        return
    }
    val strokeWidth = when {
        compact -> 2.dp
        else -> 4.dp
    }
    if (progress == null) {
        CircularProgressIndicator(modifier = modifier, strokeWidth = strokeWidth)
    } else {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier,
            strokeWidth = strokeWidth
        )
    }
}

@Composable
fun AppProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
    )
}

@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        shape = if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE) {
            MaterialTheme.shapes.extraLarge
        } else {
            MaterialTheme.shapes.small
        },
        placeholder = { androidx.compose.material3.Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = MaterialTheme.shapes.extraLarge
    )
}
