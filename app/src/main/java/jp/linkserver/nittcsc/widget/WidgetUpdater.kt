package jp.linkserver.nittcsc.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** すべてのウィジェットインスタンスをまとめて並列更新するユーティリティ */
object WidgetUpdater {
    suspend fun updateTaskWidgets(context: Context) {
        val appContext = context.applicationContext
        val manager = GlanceAppWidgetManager(appContext)

        coroutineScope {
            launch {
                runCatching {
                    val widget = TaskHugeWidget()
                    manager.getGlanceIds(TaskHugeWidget::class.java).forEach { id ->
                        widget.update(appContext, id)
                    }
                }
            }
            launch {
                runCatching {
                    val widget = TaskSmallWidget()
                    manager.getGlanceIds(TaskSmallWidget::class.java).forEach { id ->
                        widget.update(appContext, id)
                    }
                }
            }
        }
    }

    suspend fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val manager = GlanceAppWidgetManager(appContext)

        coroutineScope {
            // TaskHugeWidget を更新
            launch {
                try {
                    val widget = TaskHugeWidget()
                    manager.getGlanceIds(TaskHugeWidget::class.java).forEach { id ->
                        widget.update(appContext, id)
                    }
                } catch (_: Exception) {}
            }

            // TaskSmallWidget を更新
            launch {
                try {
                    val widget = TaskSmallWidget()
                    manager.getGlanceIds(TaskSmallWidget::class.java).forEach { id ->
                        widget.update(appContext, id)
                    }
                } catch (_: Exception) {}
            }

            // TimetableSmallWidget を更新
            launch {
                try {
                    val widget = TimetableSmallWidget()
                    manager.getGlanceIds(TimetableSmallWidget::class.java).forEach { id ->
                        widget.update(appContext, id)
                    }
                } catch (_: Exception) {}
            }

            // TimetableHugeWidget を更新
            launch {
                try {
                    val widget = TimetableHugeWidget()
                    manager.getGlanceIds(TimetableHugeWidget::class.java).forEach { id ->
                        widget.update(appContext, id)
                    }
                } catch (_: Exception) {}
            }

            // NextLessonWidget を更新
            launch {
                try {
                    val widget = NextLessonWidget()
                    manager.getGlanceIds(NextLessonWidget::class.java).forEach { id ->
                        widget.update(appContext, id)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
