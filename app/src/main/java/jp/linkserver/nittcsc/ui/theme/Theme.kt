package jp.linkserver.nittcsc.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import jp.linkserver.nittcsc.InternalFeatureFlags
import jp.linkserver.nittcsc.data.UiDesignMode

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = Error
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = Error
)

private val FixedErrorLight = Color(0xFFFF0000)
private val FixedErrorDark = Color(0xFFFF3B30)
private val FixedOnError = Color(0xFFFFFFFF)
private val FixedErrorContainer = Color(0xFFFFE8E6)
private val FixedOnErrorContainer = Color(0xFFD50000)

@Composable
fun AppTheme(
    uiDesignMode: UiDesignMode,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) {
                dynamicDarkColorScheme(context).copy(
                    error = FixedErrorDark,
                    onError = FixedOnError,
                    errorContainer = FixedErrorContainer,
                    onErrorContainer = FixedOnErrorContainer
                )
            } else {
                dynamicLightColorScheme(context).copy(
                    error = FixedErrorLight,
                    onError = FixedOnError,
                    errorContainer = FixedErrorContainer,
                    onErrorContainer = FixedOnErrorContainer
                )
            }
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val effectiveMode = uiDesignMode.effective(InternalFeatureFlags.MATERIAL_3_EXPRESSIVE)
    val typography = when (effectiveMode) {
        UiDesignMode.MATERIAL_3 -> Typography
        UiDesignMode.MATERIAL_3_EXPRESSIVE -> ExpressiveTypography
    }
    val shapes = when (effectiveMode) {
        UiDesignMode.MATERIAL_3 -> Shapes
        UiDesignMode.MATERIAL_3_EXPRESSIVE -> ExpressiveShapes
    }

    CompositionLocalProvider(LocalUiDesignMode provides effectiveMode) {
        when (effectiveMode) {
            UiDesignMode.MATERIAL_3 -> MaterialTheme(
                colorScheme = colorScheme,
                typography = typography,
                shapes = shapes,
                content = content
            )
            UiDesignMode.MATERIAL_3_EXPRESSIVE -> MaterialExpressiveTheme(
                colorScheme = colorScheme,
                motionScheme = MotionScheme.expressive(),
                typography = typography,
                shapes = shapes,
                content = content
            )
        }
    }
}

val LocalUiDesignMode = staticCompositionLocalOf { UiDesignMode.MATERIAL_3 }

@Composable
fun NittcSchedulerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    AppTheme(
        uiDesignMode = UiDesignMode.MATERIAL_3,
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}
