package dev.dopl.soundcontrol

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeControllerTest {

    @Test
    fun `getVolumeState reflects current min max and fixed flag`() {
        val controller = VolumeController(FakeAudioManagerAdapter(volume = 7, min = 0, max = 15))

        val state = controller.getVolumeState(SoundStream.MEDIA)

        assertEquals(7, state.current)
        assertEquals(0, state.min)
        assertEquals(15, state.max)
        assertFalse(state.isFixed)
    }

    @Test
    fun `getVolumeState reports a fixed volume device`() {
        val controller = VolumeController(FakeAudioManagerAdapter(isVolumeFixed = true))

        assertTrue(controller.getVolumeState(SoundStream.MEDIA).isFixed)
    }

    @Test
    fun `setVolume updates the stream volume`() {
        val controller = VolumeController(FakeAudioManagerAdapter(volume = 5, min = 0, max = 15))

        val state = controller.setVolume(SoundStream.MEDIA, 10)

        assertEquals(10, state.current)
    }

    @Test
    fun `setVolume swallows SecurityException and returns the real state`() {
        val controller = VolumeController(
            FakeAudioManagerAdapter(volume = 5, min = 0, max = 15, throwOnChange = true)
        )

        val state = controller.setVolume(SoundStream.MEDIA, 10)

        assertEquals(5, state.current)
    }

    @Test
    fun `adjustVolume raises by one step`() {
        val controller = VolumeController(FakeAudioManagerAdapter(volume = 5, min = 0, max = 15))

        val state = controller.adjustVolume(SoundStream.MEDIA, VolumeStep.RAISE)

        assertEquals(6, state.current)
    }

    @Test
    fun `adjustVolume lowers by one step`() {
        val controller = VolumeController(FakeAudioManagerAdapter(volume = 5, min = 0, max = 15))

        val state = controller.adjustVolume(SoundStream.MEDIA, VolumeStep.LOWER)

        assertEquals(4, state.current)
    }

    @Test
    fun `adjustVolume does not go below min`() {
        val controller = VolumeController(FakeAudioManagerAdapter(volume = 0, min = 0, max = 15))

        val state = controller.adjustVolume(SoundStream.MEDIA, VolumeStep.LOWER)

        assertEquals(0, state.current)
    }

    @Test
    fun `adjustVolume swallows SecurityException`() {
        val controller = VolumeController(
            FakeAudioManagerAdapter(volume = 5, min = 0, max = 15, throwOnChange = true)
        )

        val state = controller.adjustVolume(SoundStream.MEDIA, VolumeStep.RAISE)

        assertEquals(5, state.current)
    }

    @Test
    fun `floor is 1 for ringer-linked streams and the OS min elsewhere`() {
        val controller = VolumeController(FakeAudioManagerAdapter(min = 0, max = 7))

        assertEquals(1, controller.getVolumeState(SoundStream.RING).floor)
        assertEquals(1, controller.getVolumeState(SoundStream.NOTIFICATION).floor)
        assertEquals(1, controller.getVolumeState(SoundStream.SYSTEM).floor)
        assertEquals(0, controller.getVolumeState(SoundStream.MEDIA).floor)
    }

    @Test
    fun `SYSTEM is guarded the same way as RING on devices where it is aliased to it`() {
        // Reproduces the Nothing Phone (2a)-style OEM policy where STREAM_SYSTEM shares
        // RING's volume group and inherits its ringer-mode coupling.
        val fake = FakeAudioManagerAdapter(volume = 5, min = 0, max = 7, emulateRingerLink = true)
        val controller = VolumeController(fake)

        repeat(10) { controller.adjustVolume(SoundStream.SYSTEM, VolumeStep.LOWER) }

        assertEquals(1, controller.getVolumeState(SoundStream.SYSTEM).current)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, fake.ringerModeValue)
    }

    @Test
    fun `adjustVolume on RING does not lower below the floor`() {
        val controller = VolumeController(FakeAudioManagerAdapter(volume = 1, min = 0, max = 7))

        val state = controller.adjustVolume(SoundStream.RING, VolumeStep.LOWER)

        assertEquals(1, state.current)
    }

    @Test
    fun `adjustVolume on NOTIFICATION does not lower below the floor`() {
        val controller = VolumeController(FakeAudioManagerAdapter(volume = 1, min = 0, max = 7))

        val state = controller.adjustVolume(SoundStream.NOTIFICATION, VolumeStep.LOWER)

        assertEquals(1, state.current)
    }

    @Test
    fun `adjustVolume on MEDIA can still reach zero`() {
        val controller = VolumeController(FakeAudioManagerAdapter(volume = 1, min = 0, max = 15))

        val state = controller.adjustVolume(SoundStream.MEDIA, VolumeStep.LOWER)

        assertEquals(0, state.current)
    }

    @Test
    fun `setVolume on RING clamps index 0 up to the floor`() {
        val controller = VolumeController(FakeAudioManagerAdapter(volume = 3, min = 0, max = 7))

        val state = controller.setVolume(SoundStream.RING, 0)

        assertEquals(1, state.current)
    }

    @Test
    fun `repeated RING lowering never flips a mode-linking device to vibrate`() {
        val fake = FakeAudioManagerAdapter(volume = 5, min = 0, max = 7, emulateRingerLink = true)
        val controller = VolumeController(fake)

        repeat(10) { controller.adjustVolume(SoundStream.RING, VolumeStep.LOWER) }

        assertEquals(1, controller.getVolumeState(SoundStream.RING).current)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, fake.ringerModeValue)
    }

    @Test
    fun `RING volume changes while vibrating do not switch the ringer back to normal`() {
        val fake = FakeAudioManagerAdapter(
            volume = 0,
            min = 0,
            max = 7,
            ringerModeValue = AudioManager.RINGER_MODE_VIBRATE,
            emulateRingerLink = true,
        )
        val controller = VolumeController(fake)

        controller.setVolume(SoundStream.RING, 4)

        assertEquals(AudioManager.RINGER_MODE_VIBRATE, fake.ringerModeValue)
    }

    @Test
    fun `raising RING while silent does not switch the ringer back to normal`() {
        val fake = FakeAudioManagerAdapter(
            volume = 0,
            min = 0,
            max = 7,
            ringerModeValue = AudioManager.RINGER_MODE_SILENT,
            emulateRingerLink = true,
        )
        val controller = VolumeController(fake)

        controller.adjustVolume(SoundStream.RING, VolumeStep.RAISE)

        assertEquals(AudioManager.RINGER_MODE_SILENT, fake.ringerModeValue)
    }

    @Test
    fun `setRingerMode itself still changes the mode`() {
        val fake = FakeAudioManagerAdapter(emulateRingerLink = true)
        val controller = VolumeController(fake)

        val mode = controller.setRingerMode(RingerMode.VIBRATE)

        assertEquals(RingerMode.VIBRATE, mode)
    }

    @Test
    fun `getRingerMode reflects the device ringer mode`() {
        val controller = VolumeController(
            FakeAudioManagerAdapter(ringerModeValue = AudioManager.RINGER_MODE_VIBRATE)
        )

        assertEquals(RingerMode.VIBRATE, controller.getRingerMode())
    }

    @Test
    fun `setRingerMode changes the ringer mode`() {
        val controller = VolumeController(FakeAudioManagerAdapter())

        val mode = controller.setRingerMode(RingerMode.SILENT)

        assertEquals(RingerMode.SILENT, mode)
    }

    @Test
    fun `setRingerMode swallows SecurityException and returns the real mode`() {
        val controller = VolumeController(FakeAudioManagerAdapter(throwOnChange = true))

        val mode = controller.setRingerMode(RingerMode.SILENT)

        assertEquals(RingerMode.NORMAL, mode)
    }
}
