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
import androidx.glance.layout.wrapContentWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import jp.linkserver.nittcsc.MainActivity

class NextLessonWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataHelper.load(context)
        provideContent {
            GlanceTheme {
                Content(data)
            }
        }
    }
}

class NextLessonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NextLessonWidget()
}

@Composable
private fun Content(data: WidgetData) {
    val next = WidgetDataHelper.findNextLesson(data)
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
            text = "次の授業",
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(6.dp))

        if (next == null) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "今日の授業は\n終わりました",
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                )
            }
        } else {
            val (slot, lesson, tasks) = next

            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(14.dp)
                    .padding(8.dp)
            ) {

                // スロットバッジ
                Box(
                    modifier = GlanceModifier
                        .wrapContentWidth()
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(20.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = slot.label,
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                // 科目名
                Text(
                    text = lesson.subject,
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )

                // 先生 / 場所
                val subInfo = buildString {
                    append(lesson.teacher)
                    if (!lesson.location.isNullOrBlank()) append(" / ${lesson.location}")
                }
                if (subInfo.isNotBlank()) {
                    Text(
                        text = subInfo,
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }

                // 課題
                if (tasks.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    tasks.take(2).forEach { task ->
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Vertical.CenterVertically
                        ) {
                            Text(
                                text = "●",
                                style = TextStyle(
                                    color = GlanceTheme.colors.error,
                                    fontSize = 8.sp
                                )
                            )
                            Text(
                                text = " ${task.title}",
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
}
