package com.nextgenbroadcast.mobile.middleware

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.nextgenbroadcast.mobile.core.DateUtils
import com.nextgenbroadcast.mobile.middleware.notification.AlertNotificationHelper
import java.text.SimpleDateFormat
import java.util.*

class AlertDialogActivity : AppCompatActivity() {

    private val dateFormat = SimpleDateFormat("yyy-MM-dd HH:mm", Locale.US)

    private var alertId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_dialog_alert)
        setFinishOnTouchOutside(false)

        val textViewAlertMessage = findViewById<TextView>(R.id.textViewAlertMessage)
        val textViewAlertTime = findViewById<TextView>(R.id.textViewAlertTime)
        val buttonOkDialogAlert = findViewById<View>(R.id.buttonOkDialogAlert)

        title = resources.getString(R.string.warning)
        textViewAlertMessage.movementMethod = ScrollingMovementMethod()
        intent.getStringExtra(ALERT_EFFECTIVE_TIME)?.let { time ->
            textViewAlertTime.text = DateUtils.parse(time, null)?.let { date ->
                dateFormat.format(date)
            } ?: time
        }

        intent.getStringExtra(ALERT_MESSAGE)?.let { message ->
            textViewAlertMessage.text = message
        } ?: finish()

        alertId = intent.getStringExtra(AlertNotificationHelper.ALERT_NOTIFICATION_TAG)
        buttonOkDialogAlert.setOnClickListener {
            alertId?.let { tag ->
                NotificationManagerCompat.from(this).cancel(tag, tag.hashCode())
            }
            finish()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        intent?.let {
            val textViewAlertMessage = findViewById<TextView>(R.id.textViewAlertMessage)
            // Check it's the same Alert message
            if (alertId == intent.getStringExtra(AlertNotificationHelper.ALERT_NOTIFICATION_TAG)) {
                // Just update the message if provided or close otherwise
                intent.getStringExtra(ALERT_MESSAGE)?.let { message ->
                    textViewAlertMessage.text = message
                } ?: finish()
            }
        }
    }

    override fun onBackPressed() {
        // disable closing by back button super.onBackPressed()
    }

    companion object {
        const val ALERT_MESSAGE: String = "alert_message"
        const val ALERT_EFFECTIVE_TIME: String = "effective_time"

        fun newIntent(context: Context, tag: String, msg: String? = null, effectiveTime: String? = null): Intent {
            return Intent(context, AlertDialogActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                data = Uri.parse(tag)

                putExtra(ALERT_MESSAGE, msg)
                putExtra(ALERT_EFFECTIVE_TIME, effectiveTime)
                putExtra(AlertNotificationHelper.ALERT_NOTIFICATION_TAG, tag)
            }
        }
    }

}