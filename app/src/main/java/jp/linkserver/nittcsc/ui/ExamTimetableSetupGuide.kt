package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.ui.theme.Typography as StandardTypography

internal data class ExamTimetableEntryAppearance(
    val title: String,
    val targetLabel: String? = null,
    val showNavigationIcon: Boolean = false
)

/** 実際の編集タブと案内で同じQuizの描画を使う。操作は呼び出し側で提供する。 */
@Composable
internal fun ExamTimetableEntryIcon(modifier: Modifier = Modifier, decorative: Boolean = false) {
    Icon(
        Icons.Filled.Quiz,
        contentDescription = if (decorative) null else stringResource(R.string.btn_create_exam_timetable),
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
internal fun ExamTimetableEntryTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier, style = StandardTypography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** TopAppBarのタイトル・閉じた学期選択・右端アクションを、操作できないクローズアップとして再構成する。 */
@Composable
internal fun ExamTimetableEntryPreview(appearance: ExamTimetableEntryAppearance, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { },
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (appearance.showNavigationIcon) {
                Icon(Icons.Filled.Menu, null, Modifier.padding(end = 12.dp).size(24.dp))
            }
            ExamTimetableEntryTitle(appearance.title, Modifier.weight(1f))
            appearance.targetLabel?.let { label ->
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                    ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                        Row(
                            Modifier.widthIn(max = 140.dp).padding(ButtonDefaults.TextButtonContentPadding),
                            verticalAlignment = Alignment.CenterVertically
                        ) { TimetableTargetLabel(label, Modifier.weight(1f, fill = false)) }
                    }
                }
            }
            // 48dpの実際のアクション位置を保ち、押す場所だけを控えめに示す。
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    ExamTimetableEntryIcon(Modifier.padding(8.dp).size(24.dp), decorative = true)
                }
            }
        }
    }
}

@Composable
internal fun ExamTimetableSetupDialog(
    appearance: ExamTimetableEntryAppearance,
    onDismiss: () -> Unit,
    onConfigure: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_exam_unset_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.dialog_exam_unset_message))
                ExamTimetableEntryPreview(appearance)
                Text(stringResource(R.string.dialog_exam_unset_question))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } },
        confirmButton = { TextButton(onClick = onConfigure) { Text(stringResource(R.string.btn_configure_exam_timetable)) } }
    )
}
