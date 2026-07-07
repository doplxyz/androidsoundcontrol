package dev.dopl.soundcontrol

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
}
