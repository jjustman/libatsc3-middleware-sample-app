package com.nextgenbroadcast.mobile.middleware

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.nextgenbroadcast.mobile.middleware.service.Atsc3ForegroundService

internal class ServiceDialogActivity : AppCompatActivity() {

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                Atsc3ForegroundService.openRoute(this, it.toString())
            }

            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_dialog)

        setFinishOnTouchOutside(true)

        title = getString(R.string.service_action_menu_title)

        findViewById<View>(R.id.select_pcap).setOnClickListener {
            openFileChooser()
        }

        findViewById<View>(R.id.disconnect_service).setOnClickListener {
            disconnectService()
        }

        findViewById<View>(R.id.watch_tv).setOnClickListener {
            watchTV()
        }
    }

    private fun watchTV() {
        startTVApplication(this)
        finish()
    }

    private fun openFileChooser() {
        try {
            getContent.launch(CONTENT_TYPE)
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(this, "There is no one File Manager registered in system.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun disconnectService() {
        Atsc3ForegroundService.closeRoute(this)

        finish()
    }

    companion object {
        val TAG: String = ServiceDialogActivity::class.java.simpleName

        private const val CONTENT_TYPE = "*/*"
    }
}