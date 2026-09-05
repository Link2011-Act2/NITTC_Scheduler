package jp.linkserver.nittcsc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import jp.linkserver.nittcsc.data.AppDatabase
import jp.linkserver.nittcsc.data.SchedulerRepository
import jp.linkserver.nittcsc.data.UiDesignPreferences
import jp.linkserver.nittcsc.reminder.LessonStartNotificationWorker
import jp.linkserver.nittcsc.reminder.PlanReminderWorker
import jp.linkserver.nittcsc.reminder.TaskReminderWorker
import jp.linkserver.nittcsc.sync.LocalSyncManager
import jp.linkserver.nittcsc.sync.NearbySyncManager
import jp.linkserver.nittcsc.ui.NittcSchedulerApp
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModel
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModelFactory
import jp.linkserver.nittcsc.widget.WidgetUpdateWorker
import jp.linkserver.nittcsc.widget.WidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: SchedulerViewModel by viewModels {
        val database = AppDatabase.getInstance(this)
        val repository = SchedulerRepository(database, UiDesignPreferences(this))
        val syncManager = LocalSyncManager(this, repository, database)
        val nearbySyncManager = NearbySyncManager(this, repository)
        SchedulerViewModelFactory(repository, syncManager, nearbySyncManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiDesignMode by viewModel.uiDesignMode.collectAsStateWithLifecycle()
            AppTheme(uiDesignMode = uiDesignMode) {
                NittcSchedulerApp(viewModel = viewModel)
            }
        }
        // WorkManager による定期ウィジェット更新をスケジュール
        WidgetUpdateWorker.schedule(this)
        lifecycleScope.launch {
            TaskReminderWorker.rescheduleAll(this@MainActivity)
            PlanReminderWorker.rescheduleAll(this@MainActivity)
            LessonStartNotificationWorker.rescheduleAll(this@MainActivity)
        }
    }

    override fun onResume() {
        super.onResume()
        // 年度切替を先に反映してから、ウィジェットと同期を更新する
        lifecycleScope.launch {
            viewModel.refreshAcademicYear()
            WidgetUpdater.updateTaskWidgets(this@MainActivity)
            WidgetUpdater.updateAll(this@MainActivity)
            viewModel.runAutoSync()
        }
    }
}
