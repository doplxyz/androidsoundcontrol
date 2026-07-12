package dev.dopl.soundcontrol

import android.media.AudioManager

class FakeAudioManagerAdapter(
    private var volume: Int = 5,
    private val min: Int = 0,
    private val max: Int = 15,
    override val isVolumeFixed: Boolean = false,
    var ringerModeValue: Int = AudioManager.RINGER_MODE_NORMAL,
    private val throwOnChange: Boolean = false,
    /**
     * Emulates AudioService's ringer-mode linkage: lowering at index <= 1 flips the
     * ringer to vibrate (before reaching 0), and raising or setting a positive volume
     * while silenced flips it back to normal.
     */
    private val emulateRingerLink: Boolean = false,
) : AudioManagerAdapter {

    override fun getStreamVolume(streamType: Int): Int = volume

    override fun getStreamMinVolume(streamType: Int): Int = min

    override fun getStreamMaxVolume(streamType: Int): Int = max

    override fun setStreamVolume(streamType: Int, index: Int, flags: Int) {
        if (throwOnChange) throw SecurityException("denied")
        volume = index.coerceIn(min, max)
        if (emulateRingerLink) {
            ringerModeValue = if (volume == 0) {
                AudioManager.RINGER_MODE_VIBRATE
            } else {
                AudioManager.RINGER_MODE_NORMAL
            }
        }
    }

    override fun adjustStreamVolume(streamType: Int, direction: Int, flags: Int) {
        if (throwOnChange) throw SecurityException("denied")
        if (emulateRingerLink && direction < 0 && volume <= 1) {
            volume = 0
            ringerModeValue = AudioManager.RINGER_MODE_VIBRATE
            return
        }
        volume = (volume + direction).coerceIn(min, max)
        if (emulateRingerLink && direction > 0) {
            ringerModeValue = AudioManager.RINGER_MODE_NORMAL
        }
    }

    override val ringerMode: Int
        get() = ringerModeValue

    override fun setRingerMode(mode: Int) {
        if (throwOnChange) throw SecurityException("denied")
        ringerModeValue = mode
    }
}
