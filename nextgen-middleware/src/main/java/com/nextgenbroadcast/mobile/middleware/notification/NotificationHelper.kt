package com.nextgenbroadcast.mobile.middleware.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media.session.MediaButtonReceiver
import com.nextgenbroadcast.mobile.core.model.PlaybackState
import com.nextgenbroadcast.mobile.middleware.R
import com.nextgenbroadcast.mobile.middleware.ServiceDialogActivity

class NotificationHelper(
        private val context: Context,
        private val channelID: String
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun createMediaNotificationBuilder(title: String, text: String, state: PlaybackState, mediaToken: MediaSessionCompat.Token?): NotificationCompat.Builder {
        val dialogIntent = Intent(context, ServiceDialogActivity::class.java)
        val contentIntent = PendingIntent.getActivity(context, 0, dialogIntent, 0)
        val actionIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_PLAY_PAUSE)

        val builder = NotificationCompat.Builder(context, channelID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentIntent(contentIntent)
                .setContentTitle(title)
                .setContentText(text)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (state != PlaybackState.IDLE) {
            builder.addAction(createAction(context, android.R.drawable.ic_media_previous, R.string.notification_btn_pause_previous,
                    MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            ))

            when (state) {
                PlaybackState.PLAYING -> {
                    builder.addAction(createAction(context, android.R.drawable.ic_media_pause, R.string.notification_btn_pause_title, actionIntent))
                }

                PlaybackState.PAUSED -> {
                    builder.addAction(createAction(context, android.R.drawable.ic_media_play, R.string.notification_btn_play_title, actionIntent))
                }

                else -> {
                }
            }

            builder.addAction(createAction(context, android.R.drawable.ic_media_next, R.string.notification_btn_pause_next,
                    MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            ))

            val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2)
            if (mediaToken != null) {
                mediaStyle.setMediaSession(mediaToken)
            }
            builder.setStyle(mediaStyle)
        }

        return builder
    }

    private fun createAction(context: Context, iconResId: Int, titleResId: Int, pendingIntent: PendingIntent) =
            NotificationCompat.Action.Builder(
                    IconCompat.createWithResource(context, iconResId),
                    context.getString(titleResId),
                    pendingIntent
            ).build()

    fun createNotificationChannel(name: String) {
        notificationManager.createNotificationChannel(
                NotificationChannel(channelID, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    importance = NotificationManager.IMPORTANCE_LOW
                }
        )
    }

    fun notify(id: Int, notification: Notification) {
        notificationManager.notify(id, notification)
    }

    fun cancel(id: Int) {
        notificationManager.cancel(id)
    }
}
