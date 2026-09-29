package jp.linkserver.nittcsc.ui

import androidx.annotation.StringRes
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.HolidaySpecialLabel

internal data class HolidaySpecialLabelText(
    @field:StringRes val title: Int,
    @field:StringRes val short: Int
)

internal fun HolidaySpecialLabel.textResources(): HolidaySpecialLabelText = when (this) {
    HolidaySpecialLabel.MIDTERM -> HolidaySpecialLabelText(
        R.string.holiday_label_midterm, R.string.holiday_label_midterm_short
    )
    HolidaySpecialLabel.FINAL -> HolidaySpecialLabelText(
        R.string.holiday_label_final, R.string.holiday_label_final_short
    )
    HolidaySpecialLabel.SCHOOL_CLOSED -> HolidaySpecialLabelText(
        R.string.holiday_label_school_closed, R.string.holiday_label_school_closed_short
    )
    HolidaySpecialLabel.EXCURSION -> HolidaySpecialLabelText(
        R.string.holiday_label_excursion, R.string.holiday_label_excursion_short
    )
    HolidaySpecialLabel.EVENT -> HolidaySpecialLabelText(
        R.string.holiday_label_event, R.string.holiday_label_event_short
    )
    HolidaySpecialLabel.EXAM_RETURN -> HolidaySpecialLabelText(
        R.string.holiday_label_exam_return, R.string.holiday_label_exam_return_short
    )
}
