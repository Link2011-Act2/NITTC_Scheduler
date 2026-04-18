package jp.linkserver.nittcsc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import jp.linkserver.nittcsc.data.AppDatabase
import jp.linkserver.nittcsc.data.SchedulerRepository
import jp.linkserver.nittcsc.ui.NittcSchedulerApp
import jp.linkserver.nittcsc.ui.theme.NittcSchedulerTheme
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModel
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModelFactory

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
    }
}
