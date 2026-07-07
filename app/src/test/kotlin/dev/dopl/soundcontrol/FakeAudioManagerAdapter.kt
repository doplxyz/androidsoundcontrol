package dev.dopl.soundcontrol

class FakeAudioManagerAdapter(
    private var volume: Int = 5,
    private val min: Int = 0,
    private val max: Int = 15,
    override val isVolumeFixed: Boolean = false,
    private val throwOnChange: Boolean = false,
) : AudioManagerAdapter {

    override fun getStreamVolume(streamType: Int): Int = volume

    override fun getStreamMinVolume(streamType: Int): Int = min

    override fun getStreamMaxVolume(streamType: Int): Int = max

    override fun setStreamVolume(streamType: Int, index: Int, flags: Int) {
        if (throwOnChange) throw SecurityException("denied")
        volume = index.coerceIn(min, max)
    }

    override fun adjustStreamVolume(streamType: Int, direction: Int, flags: Int) {
        if (throwOnChange) throw SecurityException("denied")
        volume = (volume + direction).coerceIn(min, max)
    }
}
