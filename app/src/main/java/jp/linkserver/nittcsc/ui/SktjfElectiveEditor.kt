package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.SktjfSelectionGroupEntity
import jp.linkserver.nittcsc.sktjf.SktjfElectiveEditGroup
import kotlinx.coroutines.launch

@Composable
internal fun SktjfElectiveEditor(groups: List<SktjfElectiveEditGroup>,
    onSelect: suspend (SktjfSelectionGroupEntity,String?)->Boolean) {
    val scope=rememberCoroutineScope()
    groups.forEach { entry ->
        val group=entry.group
        var failed by remember(group.groupId,group.selectedLessonId) { mutableStateOf(false) }
        Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp).selectableGroup()) {
                Text(stringResource(R.string.sktjf_elective_title),style=MaterialTheme.typography.titleSmall)
                if(group.selectedLessonId!=null && entry.options.none { it.sourceLessonId==group.selectedLessonId })
                    Text(stringResource(R.string.sktjf_elective_missing),color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodySmall)
                val choices=entry.options.mapNotNull { item -> item.sourceLessonId?.let { it to item.content.subject } } +
                    (null to stringResource(R.string.sktjf_elective_unknown))
                choices.forEach { (id,label) ->
                    val selected=group.selectedLessonId==id
                    Row(Modifier.fillMaxWidth().selectable(selected=selected,role=Role.RadioButton,onClick={
                        scope.launch { failed=!onSelect(group,id) }
                    }).padding(vertical=2.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected=selected,onClick=null)
                        Text(label,style=MaterialTheme.typography.bodyMedium)
                    }
                }
                if(failed)Text(stringResource(R.string.sktjf_elective_save_failed),color=MaterialTheme.colorScheme.error,
                    style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
