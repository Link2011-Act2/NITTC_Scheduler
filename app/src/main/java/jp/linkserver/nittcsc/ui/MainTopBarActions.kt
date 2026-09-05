package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.ui.components.AppIconButton

@Composable
internal fun MainActionsOverflowMenu(
    showAiImport: Boolean,
    onOpenAiImport: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AppIconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.cd_more_actions)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (showAiImport) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cd_ai_import)) },
                    onClick = {
                        expanded = false
                        onOpenAiImport()
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.AutoFixHigh, contentDescription = null)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sync_title_local_sync)) },
                onClick = {
                    expanded = false
                    onOpenSync()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.sync_desktop),
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title)) },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Settings, contentDescription = null)
                }
            )
        }
    }
}

@Composable
internal fun TimetableTargetDropdown(
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.getOrNull(selectedIndex).orEmpty()
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = selectedLabel,
                modifier = Modifier.widthIn(max = 140.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelectedIndexChange(index)
                    },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }
}
