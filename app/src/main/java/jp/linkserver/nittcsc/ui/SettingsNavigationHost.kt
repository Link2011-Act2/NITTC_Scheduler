package jp.linkserver.nittcsc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

internal enum class SettingsDestination { OVERVIEW, LESSON, EXAM, SPECIAL, LESSON_NOTIFICATIONS }

/** 同じモーションと保存領域を、時間割設定と通知設定の詳細画面で共有する。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsNavigationHost(
    destination: SettingsDestination,
    onNavigate: (SettingsDestination) -> Unit,
    content: @Composable (SettingsDestination, (SettingsDestination) -> Unit) -> Unit
) {
    val transition = updateTransition(destination, label = "settings-navigation")
    val stateHolder = rememberSaveableStateHolder()
    var navigationRequested by remember { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme
    LaunchedEffect(transition.currentState, transition.targetState, transition.isRunning) {
        if (!transition.isRunning && transition.currentState == transition.targetState) navigationRequested = false
    }
    val navigate: (SettingsDestination) -> Unit = { target ->
        if (!navigationRequested && !transition.isRunning && transition.currentState == transition.targetState &&
            target != destination) {
            navigationRequested = true
            onNavigate(target)
        }
    }
    BackHandler(destination != SettingsDestination.OVERVIEW || transition.isRunning || navigationRequested) {
        navigate(SettingsDestination.OVERVIEW)
    }
    transition.AnimatedContent(
        transitionSpec = {
            if (targetState == SettingsDestination.OVERVIEW) {
                slideInHorizontally(motion.defaultSpatialSpec()) { -it / 2 } + fadeIn(motion.defaultEffectsSpec()) togetherWith
                    slideOutHorizontally(motion.defaultSpatialSpec()) { it } + fadeOut(motion.defaultEffectsSpec())
            } else {
                slideInHorizontally(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()) togetherWith
                    slideOutHorizontally(motion.defaultSpatialSpec()) { -it / 2 } + fadeOut(motion.defaultEffectsSpec())
            }
        }
    ) { page ->
        stateHolder.SaveableStateProvider(page.name) {
            Box(Modifier.testTag("settings-page-${page.name}")) { content(page, navigate) }
        }
    }
}
