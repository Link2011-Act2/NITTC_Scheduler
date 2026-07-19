package jp.linkserver.nittcsc.ui

import androidx.compose.runtime.saveable.Saver
import java.time.LocalDate

/**
 * 画面サイズや折りたたみ状態の変更で Activity が再生成されても日付入力を復元する。
 */
internal val LocalDateSaver = Saver<LocalDate, Long>(
    save = { date -> date.toEpochDay() },
    restore = { epochDay -> LocalDate.ofEpochDay(epochDay) }
)
