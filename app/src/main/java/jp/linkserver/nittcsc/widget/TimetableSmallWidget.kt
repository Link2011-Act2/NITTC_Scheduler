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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import jp.linkserver.nittcsc.MainActivity
import jp.linkserver.nittcsc.data.DayType
import androidx.compose.ui.graphics.Color as ComposeColor

private val WidgetTaskDotColor = ColorProvider(ComposeColor(0xFFBA1A1A))
private val WidgetPlanDotColor = ColorProvider(ComposeColor(0xFF2E86C1))
private val WidgetCancelledTextColor = ColorProvider(ComposeColor(0xFF8A949B))

class TimetableSmallWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataHelper.load(context)
        provideContent {
            GlanceTheme {
                Content(data)
            }
        }
    }
}

class TimetableSmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TimetableSmallWidget()
}

@Composable
private fun Content(data: WidgetData) {
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
        // ヘッダー
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "今日の時間割",
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1
            )

            val todayEntity = data.dayTypeEntities[data.today]
            val dayTypeText = WidgetDataHelper.dayTypeDisplayText(
                dayType = data.dayType,
                overrideLessonDayOfWeek = todayEntity?.overrideLessonDayOfWeek,
                regularDayLabel = if (data.settings?.enableAbTimetable == false)
                    context.getString(jp.linkserver.nittcsc.R.string.daytype_regular) else null
            )
            Text(
                text = dayTypeText,
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(modifier = GlanceModifier.height(4.dp))

        val isExamToday = WidgetDataHelper.isExamScheduleDate(data, data.today)
        if (data.dayType == DayType.HOLIDAY && !isExamToday) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "今日は休日です",
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                )
            }
        } else {
            // 最大7コマ。コマ数に応じてフォントサイズ・行間を動的調整
            val slots = WidgetDataHelper.classSlotsForDate(data, data.today).take(7)
            val count = slots.size
            // 4コマ以下はゆとり、5〜7は段階的に縮小
            val bodySize = when {
                count <= 4 -> 11.sp
                count == 5 -> 10.sp
                count == 6 -> 9.sp
                else       -> 8.sp
            }
            val labelSize = when {
                count <= 4 -> 10.sp
                count == 5 -> 9.sp
                count == 6 -> 8.sp
                else       -> 7.sp
            }
            val dotSize = when {
                count <= 5 -> 7.sp
                else       -> 6.sp
            }
            val rowTopPad = when {
                count <= 4 -> 4.dp
                count == 5 -> 3.dp
                count == 6 -> 2.dp
                else       -> 1.dp
            }
            val labelWidth = when {
                count <= 6 -> 22.dp
                else       -> 20.dp
            }

            slots.forEach { slot ->
                val lesson = WidgetDataHelper.resolveLesson(data, data.today, slot.index)
                val isCancelled = !isExamToday && data.cancelledLessons.contains(data.today to slot.index)
                val hasTasks = WidgetDataHelper.hasTasksForLesson(data, lesson)
                val hasPlans = WidgetDataHelper.hasPlansForLesson(data, lesson)

                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = rowTopPad),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    val shortLabel = slot.label.substringBefore("校時").take(4)
                    Text(
                        text = shortLabel,
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = labelSize,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.width(labelWidth)
                    )
                    Text(
                        text = lesson?.subject?.take(10) ?: "—",
                        style = TextStyle(
                            color = if (isCancelled) WidgetCancelledTextColor else GlanceTheme.colors.onBackground,
                            fontSize = bodySize,
                            fontWeight = if (lesson != null && !isCancelled) FontWeight.Medium else FontWeight.Normal,
                            textDecoration = if (isCancelled) TextDecoration.LineThrough else null
                        ),
                        modifier = GlanceModifier.defaultWeight(),
                        maxLines = 1
                    )
                    if (hasTasks) {
                        Text(
                            text = "●",
                            style = TextStyle(
                                color = WidgetTaskDotColor,
                                fontSize = dotSize
                            )
                        )
                    }
                    if (hasPlans) {
                        Text(
                            text = "●",
                            style = TextStyle(
                                color = WidgetPlanDotColor,
                                fontSize = dotSize
                            )
                        )
                    }
                }
            }
        }
    }
}
