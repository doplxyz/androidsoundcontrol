package dev.dopl.soundcontrol

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Switching to [RingerMode.SILENT] requires "Do Not Disturb" policy access
 * (ACCESS_NOTIFICATION_POLICY), which is a separate user grant from the manifest
 * permission and is not tied to AudioManager, so it lives outside VolumeController.
 */
object DndAccess {

    fun isGranted(context: Context): Boolean {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.isNotificationPolicyAccessGranted
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
}
