package com.example.lifelog.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.lifelog.MainActivity
import com.example.lifelog.R

class CheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(notificationManager)

        val checkInIntent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_CHECK_IN)
            .putExtra(EXTRA_ENTRY_ID, entryId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            entryId.hashCode(),
            checkInIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFBA7517.toInt())
            .setContentTitle("Time Audit")
            .setContentText("What have you been up to?")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_HIGH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(entryId.hashCode(), notification)
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // Channel sound/vibration is locked at creation time; delete the old
        // silent v1 channel so users who installed pre-v2 pick up the audible
        // config. Idempotent: no-op if the channel doesn't exist.
        runCatching { notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }

        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Check-ins",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Interval reminders for Time Audit"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                audioAttrs
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 200, 250)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_CHECK_IN = "com.example.lifelog.action.CHECK_IN"
        const val EXTRA_ENTRY_ID = "entry_id"
        private const val CHANNEL_ID = "time_audit_check_ins_v2"
        private const val LEGACY_CHANNEL_ID = "time_audit_check_ins"
    }
}
