package com.nextgenbroadcast.mobile.middleware.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.nextgenbroadcast.mobile.middleware.AlertDialogActivity
import com.nextgenbroadcast.mobile.middleware.R

class AlertNotificationHelper(
    private val context: Context
) {

    private val notificationManager by lazy {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).also { nm ->
            NotificationChannel(
                ALERT_CHANNEL_ID,
                ALERT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(
                    Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.beep_attention_signal_440_hz_3}"),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }.also {
                nm.createNotificationChannel(it)
            }
        }
    }

    fun showNotification(msg: String, msgTag: String, msgTime: String?) {
        val aeaId = msgTag.hashCode()

        if (notificationManager.isNotificationPolicyAccessGranted) {
            // bypass DND for alerts
            val currentInterruptionFilter = notificationManager.currentInterruptionFilter
            if (currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                && currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALARMS
            ) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
            }
        }

        /*
        Do NOT change sound level for now

        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        if (originalVolume == 0) {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            audioManager.setStreamVolume(AudioManager.STREAM_RING, maxVolume / 2, 0)
        }
         */

        val intent = Intent(context, AlertNotificationReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra(ALERT_NOTIFICATION_TAG, msgTag)
        }

        val contentIntent = AlertDialogActivity.newIntent(context, msgTag, msg, msgTime)

        val pendingContentIntent = PendingIntent.getActivity(
            context,
            aeaId,
            contentIntent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )

        val pendingDismissIntent = PendingIntent.getBroadcast(
            context,
            aeaId,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )

        val icon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_warning)
        val action = NotificationCompat.Action.Builder(
            0, context.getString(R.string.dismiss), pendingDismissIntent
        ).build()

        val builder = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(context.getString(R.string.warning))
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .addAction(action)
            .setLargeIcon(icon)
            .setContentIntent(pendingContentIntent)
            .setGroup(ALERT_GROUP_ID)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        val notification = builder.build().apply {
            flags = Notification.FLAG_NO_CLEAR
        }
        notificationManager.notify(msgTag, aeaId, notification)
    }

    companion object {
        const val ALERT_CHANNEL_ID = "2345"
        const val ALERT_GROUP_ID = "4765"
        const val ALERT_CHANNEL_NAME = "alert_channel"
        const val ALERT_NOTIFICATION_TAG = "notificationTag"
        const val ACTION_DISMISS = "DISMISS"
    }
}