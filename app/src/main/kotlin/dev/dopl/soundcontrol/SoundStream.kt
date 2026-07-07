package dev.dopl.soundcontrol

import android.media.AudioManager
import androidx.annotation.StringRes

enum class SoundStream(val streamType: Int, @StringRes val labelResId: Int) {
    MEDIA(AudioManager.STREAM_MUSIC, R.string.stream_media),
}
