package dev.dopl.soundcontrol

import android.media.AudioManager

data class VolumeState(
    val current: Int,
    val min: Int,
    val max: Int,
    val isFixed: Boolean,
    /** Lowest index the app itself will set. See [VolumeController.ringerLinkedStreams]. */
    val floor: Int,
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

    /**
     * RING and NOTIFICATION are the streams AudioService silently links to ringer mode:
     * lowering at index 1 (before even reaching 0) can auto-switch the ringer to
     * vibrate/silent, and raising or setting a volume while silenced can auto-switch it
     * back to normal. SYSTEM joins this set because on some OEM audio policies (observed
     * on Nothing OS, not on One UI) it is aliased to RING's volume group and inherits the
     * same coupling; on devices where it isn't aliased, guarding it is a harmless no-op.
     * OEMs vary in exactly when they flip. To keep the volume controls fully independent
     * of the ringer-mode selector, this class defends in two layers: an index floor of 1
     * (never enter the flip zone from above), plus [withRingerModeGuard] (if the OS flips
     * the mode anyway, immediately restore it). Vibrate/silent stay reachable only
     * through [setRingerMode].
     */
    private val ringerLinkedStreams = setOf(SoundStream.RING, SoundStream.NOTIFICATION, SoundStream.SYSTEM)

    private fun floorFor(stream: SoundStream): Int {
        val osMin = audioManager.getStreamMinVolume(stream.streamType)
        return if (stream in ringerLinkedStreams) maxOf(osMin, 1) else osMin
    }

    private inline fun withRingerModeGuard(stream: SoundStream, op: () -> Unit) {
        if (stream !in ringerLinkedStreams) {
            op()
            return
        }
        val before = audioManager.ringerMode
        op()
        if (audioManager.ringerMode != before) {
            try {
                audioManager.setRingerMode(before)
            } catch (_: SecurityException) {
            }
        }
    }

    fun getVolumeState(stream: SoundStream): VolumeState {
        val streamType = stream.streamType
        return VolumeState(
            current = audioManager.getStreamVolume(streamType),
            min = audioManager.getStreamMinVolume(streamType),
            max = audioManager.getStreamMaxVolume(streamType),
            isFixed = audioManager.isVolumeFixed,
            floor = floorFor(stream),
        )
    }

    fun setVolume(stream: SoundStream, index: Int): VolumeState {
        withRingerModeGuard(stream) {
            try {
                audioManager.setStreamVolume(stream.streamType, maxOf(index, floorFor(stream)), 0)
            } catch (_: SecurityException) {
            }
        }
        return getVolumeState(stream)
    }

    fun adjustVolume(stream: SoundStream, step: VolumeStep): VolumeState {
        val atFloor = step == VolumeStep.LOWER &&
            audioManager.getStreamVolume(stream.streamType) <= floorFor(stream)
        if (!atFloor) {
            withRingerModeGuard(stream) {
                try {
                    audioManager.adjustStreamVolume(stream.streamType, step.adjustDirection, 0)
                } catch (_: SecurityException) {
                }
            }
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
