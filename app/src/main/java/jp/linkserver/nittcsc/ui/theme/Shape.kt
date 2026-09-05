package jp.linkserver.nittcsc.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/** M3Eのヒーロー領域用。非対称な輪郭で通常カードとの役割差を明確にする。 */
val ExpressiveLessonHeroShape = RoundedCornerShape(
    topStart = 36.dp,
    topEnd = 36.dp,
    bottomEnd = 12.dp,
    bottomStart = 36.dp
)

/** 課題の締切ヒーロー用。授業ヒーローとは異なる輪郭でコンテンツを識別する。 */
val ExpressiveTaskHeroShape = RoundedCornerShape(
    topStart = 12.dp,
    topEnd = 32.dp,
    bottomEnd = 32.dp,
    bottomStart = 32.dp
)

val ExpressiveFabShape = RoundedCornerShape(
    topStart = 28.dp,
    topEnd = 28.dp,
    bottomEnd = 10.dp,
    bottomStart = 28.dp
)
