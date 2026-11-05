package jp.linkserver.nittcsc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import jp.linkserver.pyrite.screen.PyriteNavigationDirection
import jp.linkserver.pyrite.screen.PyriteScreenTransition

internal enum class SettingsDestination { OVERVIEW, LESSON, EXAM, SPECIAL, LESSON_NOTIFICATIONS }

internal fun settingsNavigationDirection(initial: SettingsDestination, target: SettingsDestination): PyriteNavigationDirection =
    if (initial != SettingsDestination.OVERVIEW && target == SettingsDestination.OVERVIEW)
        PyriteNavigationDirection.Backward else PyriteNavigationDirection.Forward

/** 設定内のモーション・クリッピング・画面別の保存領域はPyriteに委ねる。 */
@Composable
internal fun SettingsNavigationHost(
    destination: SettingsDestination,
    onNavigate: (SettingsDestination) -> Unit,
    content: @Composable (SettingsDestination, (SettingsDestination) -> Unit) -> Unit
) {
    val stateHolder = rememberSaveableStateHolder()
    val renderedScreens = remember { mutableStateListOf<SettingsDestination>() }
    var navigationRequested by remember { mutableStateOf(false) }
    val settled = renderedScreens.size == 1 && renderedScreens.single() == destination
    LaunchedEffect(destination, settled) {
        if (settled) navigationRequested = false
    }
    val navigate: (SettingsDestination) -> Unit = { target ->
        if (!navigationRequested && settled && target != destination) {
            navigationRequested = true
            onNavigate(target)
        }
    }
    BackHandler(destination != SettingsDestination.OVERVIEW || !settled || navigationRequested) {
        navigate(SettingsDestination.OVERVIEW)
    }
    PyriteScreenTransition(
        targetState = destination,
        direction = ::settingsNavigationDirection,
        contentKey = { it.name },
        stateHolder = stateHolder
    ) { screen ->
        // 退出画面の破棄はPyriteの実際の遷移完了に従う。別のTransitionやタイマーは作らない。
        DisposableEffect(screen) {
            renderedScreens.add(screen)
            onDispose { renderedScreens.remove(screen) }
        }
        Box(Modifier.testTag("settings-page-${screen.name}")) { content(screen, navigate) }
    }
}
