package jp.linkserver.nittcsc.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 設定画面と同じ、進む・戻るに応じた横スライドとフェード。 */
@Composable
internal fun <T> QrShareNavigation(
    targetState: T,
    isReturning: (T) -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            if (isReturning(targetState)) {
                slideInHorizontally { -it / 2 } + fadeIn() togetherWith
                    slideOutHorizontally { it } + fadeOut()
            } else {
                slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it / 2 } + fadeOut()
            }
        },
        label = "qr-share-navigation"
    ) { page -> content(page) }
}
