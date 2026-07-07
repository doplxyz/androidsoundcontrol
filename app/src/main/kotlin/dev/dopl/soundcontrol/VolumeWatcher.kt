package dev.dopl.soundcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Watches for volume/ringer-mode changes made outside this app (hardware keys, Quick
 * Settings, other apps) using only public APIs, and invokes [onChanged] so the caller
 * can re-sync from AudioManager, which is always the source of truth.
 *
 * Settings.System covers descendant changes broadly (not just audio), so onChanged may
 * fire for unrelated system settings too; re-reading AudioManager on every call is cheap
 * enough that filtering isn't worth the complexity.
 */
class VolumeWatcher(
    private val context: Context,
    private val onChanged: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())

    private val settingsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            onChanged()
        }
    }

    private val ringerModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onChanged()
        }
    }

    private var isRegistered = false

    fun start() {
        if (isRegistered) return
        isRegistered = true
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            settingsObserver,
        )
        ContextCompat.registerReceiver(
            context,
            ringerModeReceiver,
            IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun stop() {
        if (!isRegistered) return
        isRegistered = false
        context.contentResolver.unregisterContentObserver(settingsObserver)
        context.unregisterReceiver(ringerModeReceiver)
    }
}
