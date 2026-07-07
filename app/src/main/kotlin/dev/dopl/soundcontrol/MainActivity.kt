package dev.dopl.soundcontrol

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import dev.dopl.soundcontrol.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var volumeController: VolumeController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeController = VolumeController(SystemAudioManagerAdapter(audioManager))

        binding.volumeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val min = volumeController.getVolumeState(SoundStream.MEDIA).min
                renderVolumeState(volumeController.setVolume(SoundStream.MEDIA, progress + min))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.btnVolumeDown.setOnClickListener {
            renderVolumeState(volumeController.adjustVolume(SoundStream.MEDIA, VolumeStep.LOWER))
        }
        binding.btnVolumeUp.setOnClickListener {
            renderVolumeState(volumeController.adjustVolume(SoundStream.MEDIA, VolumeStep.RAISE))
        }
    }

    override fun onResume() {
        super.onResume()
        renderVolumeState(volumeController.getVolumeState(SoundStream.MEDIA))
    }

    private fun renderVolumeState(state: VolumeState) {
        binding.volumeSeekbar.max = state.max - state.min
        binding.volumeSeekbar.progress = state.current - state.min
        binding.volumeSeekbar.isEnabled = !state.isFixed
        binding.btnVolumeDown.isEnabled = !state.isFixed
        binding.btnVolumeUp.isEnabled = !state.isFixed
        binding.volumeValue.text = getString(R.string.volume_value_format, state.current, state.max)
    }
}
