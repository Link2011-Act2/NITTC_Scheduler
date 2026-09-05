package jp.linkserver.nittcsc.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import java.time.LocalDateTime
import java.time.LocalTime
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
