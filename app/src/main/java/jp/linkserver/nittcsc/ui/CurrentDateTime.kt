package jp.linkserver.nittcsc.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

/**
 * Returns a minute-updated wall clock only while the calling feature is active.
 * Reading the full date-time on every tick also keeps day-boundary UI accurate.
 */
@Composable
internal fun rememberCurrentDateTime(enabled: Boolean): LocalDateTime? {
    if (!enabled) return null

    val currentDateTime by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            val delayMs = 60_000L - (System.currentTimeMillis() % 60_000L) + 50L
            delay(delayMs)
        }
    }
    return currentDateTime
}

@Composable
internal fun rememberCurrentTime(): LocalTime =
    checkNotNull(rememberCurrentDateTime(enabled = true)).toLocalTime()

/** 日付変更とアプリ復帰時に、現在の学期の候補を更新する。 */
@Composable
internal fun rememberCurrentDate(): LocalDate {
    var currentDate by remember { mutableStateOf(LocalDate.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentDate = LocalDate.now()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val now = ZonedDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L) + 50L)
            currentDate = LocalDate.now()
        }
    }
    return currentDate
}
