package jp.linkserver.nittcsc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import jp.linkserver.nittcsc.data.AppDatabase
import jp.linkserver.nittcsc.data.SchedulerRepository
import jp.linkserver.nittcsc.ui.NittcSchedulerApp
import jp.linkserver.nittcsc.ui.theme.NittcSchedulerTheme
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModel
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModelFactory
import jp.linkserver.nittcsc.widget.WidgetUpdateWorker
import jp.linkserver.nittcsc.widget.WidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: SchedulerViewModel by viewModels {
        val repository = SchedulerRepository(AppDatabase.getInstance(this))
        SchedulerViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NittcSchedulerTheme {
                NittcSchedulerApp(viewModel = viewModel)
            }
        }
        // WorkManager による定期ウィジェット更新をスケジュール
        WidgetUpdateWorker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        // 課題ウィジェットは最優先で更新し、その後に全体を更新
        lifecycleScope.launch {
            WidgetUpdater.updateTaskWidgets(this@MainActivity)
            WidgetUpdater.updateAll(this@MainActivity)
        }
    }
}
