package com.nextgenbroadcast.mobile.middleware.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.nextgenbroadcast.mobile.middleware.AlertDialogActivity

class AlertNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlertNotificationHelper.ACTION_DISMISS) {
            intent.getStringExtra(AlertNotificationHelper.ALERT_NOTIFICATION_TAG)?.let { tag ->
                NotificationManagerCompat.from(context).cancel(tag, tag.hashCode())
                // close alert activity too
                context.startActivity(AlertDialogActivity.newIntent(
                    context, tag
                ))
            }
        }
    }
}