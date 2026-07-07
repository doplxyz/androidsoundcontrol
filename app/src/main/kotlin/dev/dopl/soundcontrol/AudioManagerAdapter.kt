package dev.dopl.soundcontrol

/**
 * Thin abstraction over [android.media.AudioManager] so [VolumeController] can be
 * unit-tested on the JVM without Robolectric.
 */
interface AudioManagerAdapter {
    fun getStreamVolume(streamType: Int): Int
    fun getStreamMinVolume(streamType: Int): Int
    fun getStreamMaxVolume(streamType: Int): Int
    fun setStreamVolume(streamType: Int, index: Int, flags: Int)
    fun adjustStreamVolume(streamType: Int, direction: Int, flags: Int)
    val isVolumeFixed: Boolean
    val ringerMode: Int
    fun setRingerMode(mode: Int)
}
