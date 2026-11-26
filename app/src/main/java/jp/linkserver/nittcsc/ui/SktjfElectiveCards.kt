package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.sktjf.SktjfVisibleExtra

/** Alternatives share one time slot; tapping a card opens that exact candidate. */
@Composable
internal fun SktjfElectiveCards(options: List<SktjfVisibleExtra>, onOpen: (SktjfVisibleExtra)->Unit,
    modifier: Modifier=Modifier) {
    BoxWithConstraints(modifier=modifier.fillMaxSize()) {
    val scrolling=options.size>2 || maxWidth<200.dp
    Row(modifier=Modifier.fillMaxSize().then(if(scrolling) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            Surface(onClick={onOpen(option)},shape=RoundedCornerShape(10.dp),
                color=MaterialTheme.colorScheme.secondaryContainer,
                modifier=(if(scrolling) Modifier.width(156.dp) else Modifier.weight(1f)).fillMaxHeight()) {
                Column(modifier=Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(option.content.subject,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold,
                        maxLines=2,overflow=TextOverflow.Ellipsis)
                    val teacher=option.content.teachers.joinToString("、")
                    val room=option.content.rooms.joinToString("、")
                    if(teacher.isNotBlank())Text(teacher,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(room.isNotBlank())Text(room,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
    }
}
