package dev.dopl.soundcontrol

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Space
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import dev.dopl.soundcontrol.databinding.ActivityMainBinding
import dev.dopl.soundcontrol.databinding.ViewVolumeRowBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var volumeController: VolumeController
    private lateinit var volumeWatcher: VolumeWatcher
    private lateinit var layoutPreferences: LayoutPreferences
    private var isSyncingRingerMode = false
    private val streamsBeingDragged = mutableSetOf<SoundStream>()

    private val streamRows: Map<SoundStream, ViewVolumeRowBinding> by lazy {
        mapOf(
            SoundStream.RING to binding.rowRing,
            SoundStream.NOTIFICATION to binding.rowNotification,
            SoundStream.MEDIA to binding.rowMedia,
            SoundStream.ALARM to binding.rowAlarm,
            SoundStream.VOICE_CALL to binding.rowVoiceCall,
            SoundStream.SYSTEM to binding.rowSystem,
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
        layoutPreferences = LayoutPreferences(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.screenRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        streamRows.forEach { (stream, row) -> bindRow(stream, row) }

        binding.ringerModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isSyncingRingerMode || !isChecked) return@addOnButtonCheckedListener
            val mode = ringerModeButtonIds[checkedId] ?: return@addOnButtonCheckedListener
            onRingerModeSelected(mode)
        }

        binding.btnDndSettings.setOnClickListener {
            startActivity(DndAccess.settingsIntent())
        }

        binding.creditText.text = getString(
            R.string.credit_format,
            getString(R.string.app_name),
            appVersionName(),
        )

        setUpLayoutSettings()
        applyVerticalPosition()
        applyRowSpacing()
    }

    private fun setUpLayoutSettings() {
        binding.seekPosition.progress = layoutPreferences.position
        binding.seekPosition.setOnSeekBarChangeListener(seekBarListener { progress ->
            layoutPreferences.position = progress
            applyVerticalPosition()
        })

        binding.seekSpacing.progress = layoutPreferences.spacing
        binding.seekSpacing.setOnSeekBarChangeListener(seekBarListener { progress ->
            layoutPreferences.spacing = progress
            applyRowSpacing()
        })
    }

    private fun seekBarListener(onChanged: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) onChanged(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        }

    private fun applyVerticalPosition() {
        val bias = layoutPreferences.positionBias()
        setSpaceWeight(binding.spaceTop, bias)
        setSpaceWeight(binding.spaceBottom, 1f - bias)
    }

    private fun setSpaceWeight(space: Space, weight: Float) {
        val params = space.layoutParams as LinearLayout.LayoutParams
        params.weight = weight
        space.layoutParams = params
    }

    private fun applyRowSpacing() {
        val spacingPx = dpToPx(layoutPreferences.spacingDp())

        val ringerParams = binding.ringerModeGroup.layoutParams as LinearLayout.LayoutParams
        ringerParams.bottomMargin = spacingPx
        binding.ringerModeGroup.layoutParams = ringerParams

        val dndParams = binding.dndAccessBanner.layoutParams as LinearLayout.LayoutParams
        dndParams.bottomMargin = spacingPx
        binding.dndAccessBanner.layoutParams = dndParams

        streamRows.values.forEach { row ->
            row.root.setPadding(row.root.paddingLeft, spacingPx, row.root.paddingRight, spacingPx)
        }
    }

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        resources.displayMetrics,
    ).toInt()

    private fun appVersionName(): String {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        return info.versionName.orEmpty()
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
                val floor = volumeController.getVolumeState(stream).floor
                renderVolumeState(row, volumeController.setVolume(stream, progress + floor))
                refreshRingerModeSelection()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                streamsBeingDragged += stream
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                streamsBeingDragged -= stream
            }
        })

        row.btnVolumeDown.setOnClickListener {
            volumeController.adjustVolume(stream, VolumeStep.LOWER)
            refreshAll()
        }
        row.btnVolumeUp.setOnClickListener {
            volumeController.adjustVolume(stream, VolumeStep.RAISE)
            refreshAll()
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
        row.volumeSeekbar.max = state.max - state.floor
        row.volumeSeekbar.progress = (state.current - state.floor).coerceAtLeast(0)
        row.volumeSeekbar.isEnabled = enabled
        row.btnVolumeDown.isEnabled = enabled
        row.btnVolumeUp.isEnabled = enabled
        row.volumeValue.text = getString(R.string.volume_value_format, state.current, state.max)
    }
}
