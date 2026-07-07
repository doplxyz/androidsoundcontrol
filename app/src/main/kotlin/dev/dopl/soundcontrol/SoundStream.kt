package dev.dopl.soundcontrol

import android.media.AudioManager
import androidx.annotation.StringRes

enum class SoundStream(val streamType: Int, @StringRes val labelResId: Int) {
    MEDIA(AudioManager.STREAM_MUSIC, R.string.stream_media),
    RING(AudioManager.STREAM_RING, R.string.stream_ring),
    NOTIFICATION(AudioManager.STREAM_NOTIFICATION, R.string.stream_notification),
    ALARM(AudioManager.STREAM_ALARM, R.string.stream_alarm),
    VOICE_CALL(AudioManager.STREAM_VOICE_CALL, R.string.stream_voice_call),
}
