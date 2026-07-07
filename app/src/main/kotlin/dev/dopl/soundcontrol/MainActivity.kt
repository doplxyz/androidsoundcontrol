package dev.dopl.soundcontrol

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import dev.dopl.soundcontrol.databinding.ActivityMainBinding
import dev.dopl.soundcontrol.databinding.ViewVolumeRowBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var volumeController: VolumeController
    private lateinit var volumeWatcher: VolumeWatcher
    private var isSyncingRingerMode = false
    private val streamsBeingDragged = mutableSetOf<SoundStream>()

    private val streamRows: Map<SoundStream, ViewVolumeRowBinding> by lazy {
        mapOf(
            SoundStream.MEDIA to binding.rowMedia,
            SoundStream.RING to binding.rowRing,
            SoundStream.NOTIFICATION to binding.rowNotification,
            SoundStream.ALARM to binding.rowAlarm,
            SoundStream.VOICE_CALL to binding.rowVoiceCall,
        )
    }

    private val ringerModeButtonIds = mapOf(
        R.id.btn_ringer_normal to RingerMode.NORMAL,
        R.id.btn_ringer_vibrate to RingerMode.VIBRATE,
        R.id.btn_ringer_silent to RingerMode.SILENT,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeController = VolumeController(SystemAudioManagerAdapter(audioManager))
        volumeWatcher = VolumeWatcher(this) { refreshAll() }

        streamRows.forEach { (stream, row) -> bindRow(stream, row) }

        binding.ringerModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isSyncingRingerMode || !isChecked) return@addOnButtonCheckedListener
            val mode = ringerModeButtonIds[checkedId] ?: return@addOnButtonCheckedListener
            onRingerModeSelected(mode)
        }

        binding.btnDndSettings.setOnClickListener {
            startActivity(DndAccess.settingsIntent())
        }
    }

    override fun onStart() {
        super.onStart()
        volumeWatcher.start()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    override fun onStop() {
        super.onStop()
        volumeWatcher.stop()
    }

    private fun bindRow(stream: SoundStream, row: ViewVolumeRowBinding) {
        row.streamLabel.text = getString(stream.labelResId)

        row.volumeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val min = volumeController.getVolumeState(stream).min
                renderVolumeState(row, volumeController.setVolume(stream, progress + min))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                streamsBeingDragged += stream
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                streamsBeingDragged -= stream
            }
        })

        row.btnVolumeDown.setOnClickListener {
            renderVolumeState(row, volumeController.adjustVolume(stream, VolumeStep.LOWER))
        }
        row.btnVolumeUp.setOnClickListener {
            renderVolumeState(row, volumeController.adjustVolume(stream, VolumeStep.RAISE))
        }
    }

    private fun onRingerModeSelected(mode: RingerMode) {
        if (mode == RingerMode.SILENT && !DndAccess.isGranted(this)) {
            binding.dndAccessBanner.visibility = View.VISIBLE
            refreshRingerModeSelection()
            return
        }
        volumeController.setRingerMode(mode)
        refreshAll()
    }

    private fun refreshAll() {
        refreshRingerModeSelection()
        binding.dndAccessBanner.visibility =
            if (DndAccess.isGranted(this)) View.GONE else View.VISIBLE

        val ringerMode = volumeController.getRingerMode()
        streamRows.forEach { (stream, row) ->
            if (stream in streamsBeingDragged) return@forEach
            val state = volumeController.getVolumeState(stream)
            val silencedByRingerMode =
                (stream == SoundStream.RING || stream == SoundStream.NOTIFICATION) &&
                    ringerMode != RingerMode.NORMAL
            renderVolumeState(row, state, enabled = !state.isFixed && !silencedByRingerMode)
        }
    }

    private fun refreshRingerModeSelection() {
        val current = volumeController.getRingerMode()
        val buttonId = ringerModeButtonIds.entries.first { it.value == current }.key
        isSyncingRingerMode = true
        binding.ringerModeGroup.check(buttonId)
        isSyncingRingerMode = false
    }

    private fun renderVolumeState(
        row: ViewVolumeRowBinding,
        state: VolumeState,
        enabled: Boolean = !state.isFixed,
    ) {
        row.volumeSeekbar.max = state.max - state.min
        row.volumeSeekbar.progress = state.current - state.min
        row.volumeSeekbar.isEnabled = enabled
        row.btnVolumeDown.isEnabled = enabled
        row.btnVolumeUp.isEnabled = enabled
        row.volumeValue.text = getString(R.string.volume_value_format, state.current, state.max)
    }
}
