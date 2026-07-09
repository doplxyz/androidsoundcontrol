package dev.dopl.soundcontrol

import android.content.Context

/**
 * User-adjustable display preferences (vertical position of the control block and the
 * spacing between rows). These are presentation-only settings, not audio state, so unlike
 * volume/ringer mode they are safe to persist locally.
 */
class LayoutPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var position: Int
        get() = prefs.getInt(KEY_POSITION, DEFAULT_POSITION).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_POSITION, value.coerceIn(0, 100)).apply()

    var spacing: Int
        get() = prefs.getInt(KEY_SPACING, DEFAULT_SPACING).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_SPACING, value.coerceIn(0, 100)).apply()

    /** 0f = 画面上部に詰める、0.5f = 画面中央に配置する */
    fun positionBias(): Float = position / (2f * MAX_PROGRESS)

    /** 行・セクション間の余白(dp) */
    fun spacingDp(): Int = spacing * MAX_SPACING_DP / MAX_PROGRESS

    companion object {
        private const val PREFS_NAME = "layout_preferences"
        private const val KEY_POSITION = "position"
        private const val KEY_SPACING = "spacing"
        private const val MAX_PROGRESS = 100
        private const val DEFAULT_POSITION = 100
        private const val DEFAULT_SPACING = 25
        private const val MAX_SPACING_DP = 16
    }
}
