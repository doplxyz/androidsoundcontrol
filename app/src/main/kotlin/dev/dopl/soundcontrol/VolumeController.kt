package dev.dopl.soundcontrol

import android.media.AudioManager

data class VolumeState(
    val current: Int,
    val min: Int,
    val max: Int,
    val isFixed: Boolean,
)

enum class VolumeStep(val adjustDirection: Int) {
    RAISE(AudioManager.ADJUST_RAISE),
    LOWER(AudioManager.ADJUST_LOWER),
}

/**
 * The only class besides [SystemAudioManagerAdapter] allowed to touch AudioManager
 * semantics. SecurityException (e.g. DND blocking RING changes) is swallowed here so
 * callers always get back the real device state instead of crashing.
 */
class VolumeController(private val audioManager: AudioManagerAdapter) {

    fun getVolumeState(stream: SoundStream): VolumeState {
        val streamType = stream.streamType
        return VolumeState(
            current = audioManager.getStreamVolume(streamType),
            min = audioManager.getStreamMinVolume(streamType),
            max = audioManager.getStreamMaxVolume(streamType),
            isFixed = audioManager.isVolumeFixed,
        )
    }

    fun setVolume(stream: SoundStream, index: Int): VolumeState {
        try {
            audioManager.setStreamVolume(stream.streamType, index, 0)
        } catch (_: SecurityException) {
        }
        return getVolumeState(stream)
    }

    fun adjustVolume(stream: SoundStream, step: VolumeStep): VolumeState {
        try {
            audioManager.adjustStreamVolume(stream.streamType, step.adjustDirection, 0)
        } catch (_: SecurityException) {
        }
        return getVolumeState(stream)
    }

    fun getRingerMode(): RingerMode = RingerMode.fromValue(audioManager.ringerMode)

    fun setRingerMode(mode: RingerMode): RingerMode {
        try {
            audioManager.setRingerMode(mode.value)
        } catch (_: SecurityException) {
        }
        return getRingerMode()
    }
}
