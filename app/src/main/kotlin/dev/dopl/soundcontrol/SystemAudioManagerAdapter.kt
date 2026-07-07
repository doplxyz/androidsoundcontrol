package dev.dopl.soundcontrol

import android.media.AudioManager

class SystemAudioManagerAdapter(private val audioManager: AudioManager) : AudioManagerAdapter {

    override fun getStreamVolume(streamType: Int): Int =
        audioManager.getStreamVolume(streamType)

    override fun getStreamMinVolume(streamType: Int): Int =
        audioManager.getStreamMinVolume(streamType)

    override fun getStreamMaxVolume(streamType: Int): Int =
        audioManager.getStreamMaxVolume(streamType)

    override fun setStreamVolume(streamType: Int, index: Int, flags: Int) {
        audioManager.setStreamVolume(streamType, index, flags)
    }

    override fun adjustStreamVolume(streamType: Int, direction: Int, flags: Int) {
        audioManager.adjustStreamVolume(streamType, direction, flags)
    }

    override val isVolumeFixed: Boolean
        get() = audioManager.isVolumeFixed
}
