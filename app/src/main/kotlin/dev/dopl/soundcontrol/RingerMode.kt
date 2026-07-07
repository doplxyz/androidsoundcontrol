package dev.dopl.soundcontrol

import android.media.AudioManager
import androidx.annotation.StringRes

enum class RingerMode(val value: Int, @StringRes val labelResId: Int) {
    NORMAL(AudioManager.RINGER_MODE_NORMAL, R.string.ringer_mode_normal),
    VIBRATE(AudioManager.RINGER_MODE_VIBRATE, R.string.ringer_mode_vibrate),
    SILENT(AudioManager.RINGER_MODE_SILENT, R.string.ringer_mode_silent);

    companion object {
        fun fromValue(value: Int): RingerMode = entries.firstOrNull { it.value == value } ?: NORMAL
    }
}
