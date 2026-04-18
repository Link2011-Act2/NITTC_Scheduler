package jp.linkserver.nittcsc.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import androidx.glance.text.TextStyle
import jp.linkserver.nittcsc.MainActivity
import jp.linkserver.nittcsc.data.DayType
import java.time.DayOfWeek

class TimetableHugeWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataHelper.load(context)
        provideContent {
            GlanceTheme {
                Content(data)
            }
        }
    }
}

class TimetableHugeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TimetableHugeWidget()
}

@Composable
private fun Content(data: WidgetData) {
    val startOfWeek = data.today.with(DayOfWeek.MONDAY)
    val weekDates = (0..4).map { startOfWeek.plusDays(it.toLong()) }
    val context = LocalContext.current

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.background)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        // アプリの週表示に合わせて、曜日丸 + A/B/休 を表示
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Spacer(modifier = GlanceModifier.width(30.dp))
            weekDates.forEachIndexed { index, date ->
                if (index > 0) {
                    Spacer(modifier = GlanceModifier.width(2.dp))
                }
                val isToday = date == data.today
                val dayType = data.dayTypeMap[date] ?: WidgetDataHelper.defaultDayType(date)
                val dow = date.dayOfWeek.value

                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(horizontal = 1.dp),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    Box(
                        modifier = GlanceModifier
                            .width(20.dp)
                            .height(20.dp)
                            .background(
                                if (isToday) GlanceTheme.colors.primary else GlanceTheme.colors.background
                            )
                            .cornerRadius(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = WidgetDataHelper.dayLabel(dow),
                            style = TextStyle(
                                color = if (isToday) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onBackground,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                    Text(
                        text = dayTypeLabel(dayType),
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 8.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(2.dp))

        data.classSlots.forEachIndexed { i, slot ->
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .padding(vertical = 1.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // 左ラベル: 校時 + 開始/終了
                Column(
                    modifier = GlanceModifier.width(30.dp),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    Text(
                        text = "${i + 1}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    )
                    Text(
                        text = formatHm(slot.start.hour, slot.start.minute),
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 7.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                    Text(
                        text = "↕",
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 7.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                    Text(
                        text = formatHm(slot.end.hour, slot.end.minute),
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 7.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                }

                weekDates.forEachIndexed { index, date ->
                    if (index > 0) {
                        Spacer(modifier = GlanceModifier.width(2.dp))
                    }
                    val lesson = WidgetDataHelper.resolveLesson(date, slot.index, data.lessons, data.dayTypeMap)
                    val hasTasks = WidgetDataHelper.hasTasksForDate(data, date, lesson)
                    val cellBg = if (lesson != null) GlanceTheme.colors.surfaceVariant else GlanceTheme.colors.background

                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(vertical = 2.dp)
                            .background(cellBg)
                            .cornerRadius(10.dp)
                            .padding(3.dp)
                    ) {
                        if (lesson == null) {
                            Box(
                                modifier = GlanceModifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "—",
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onBackground,
                                        fontSize = 9.sp,
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                        } else {
                            Column(modifier = GlanceModifier.fillMaxSize()) {
                                Text(
                                    text = lesson.subject,
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onBackground,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 2
                                )

                                if (hasTasks) {
                                    Text(
                                        text = "●",
                                        style = TextStyle(
                                            color = GlanceTheme.colors.error,
                                            fontSize = 7.sp
                                        )
                                    )
                                }

                                Spacer(modifier = GlanceModifier.defaultWeight())

                                if (lesson.teacher.isNotBlank()) {
                                    Text(
                                        text = lesson.teacher,
                                        style = TextStyle(
                                            color = GlanceTheme.colors.onBackground,
                                            fontSize = 8.sp
                                        ),
                                        maxLines = 1
                                    )
                                }
                                if (!lesson.location.isNullOrBlank()) {
                                    Text(
                                        text = lesson.location,
                                        style = TextStyle(
                                            color = GlanceTheme.colors.onBackground,
                                            fontSize = 7.sp
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun dayTypeLabel(dayType: DayType): String = when (dayType) {
    DayType.A -> "A"
    DayType.B -> "B"
    DayType.HOLIDAY -> "休"
}

private fun formatHm(hour: Int, minute: Int): String {
    return "$hour:${minute.toString().padStart(2, '0')}"
}
