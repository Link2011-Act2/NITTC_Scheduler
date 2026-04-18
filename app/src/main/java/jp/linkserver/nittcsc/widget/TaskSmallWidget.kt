package jp.linkserver.nittcsc.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.LocalContext
import android.content.Intent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import jp.linkserver.nittcsc.MainActivity

class TaskSmallWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataHelper.load(context)
        provideContent {
            GlanceTheme {
                Content(data)
            }
        }
    }
}

class TaskSmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TaskSmallWidget()
}

@Composable
private fun Content(data: WidgetData) {
    val tasks = data.incompleteTasks.take(2)
    val context = LocalContext.current

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.background)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = "課題",
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))

        if (tasks.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "課題はありません",
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                )
            }
        } else {
            tasks.forEach { task ->
                Column(
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text(
                        text = task.title,
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Text(
                            text = task.subject.take(6),
                            style = TextStyle(
                                color = GlanceTheme.colors.onBackground,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = " | ${WidgetDataHelper.formatDueDate(task, data.today)}",
                            style = TextStyle(
                                color = GlanceTheme.colors.error,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                }
            }
        }
    }
}
