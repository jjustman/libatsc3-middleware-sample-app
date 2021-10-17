package com.nextgenbroadcast.mobile.middleware.scoreboard

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.text.Spannable
import android.text.SpannableString
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.ErrorData
import com.nextgenbroadcast.mobile.middleware.scoreboard.databinding.ActivityConsoleBinding
import com.nextgenbroadcast.mobile.middleware.scoreboard.telemetry.COMMAND_DATE_FORMAT
import com.nextgenbroadcast.mobile.middleware.scoreboard.telemetry.inCommandFormat
import com.nextgenbroadcast.mobile.middleware.scoreboard.telemetry.toInCommandFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class ConsoleActivity : AppCompatActivity() {
    enum class FlowType {
        ALL, ERROR;

        companion object {
            fun getByNameOrNull(name: String) = values().firstOrNull {
                it.name == name
            }
        }
    }

    private val hasFeaturePIP: Boolean by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private lateinit var binding: ActivityConsoleBinding

    private var serviceBinder: ScoreboardService.ScoreboardBinding? = null
    private var connectionJob: Job? = null

    private var deviceId: String? = null
    private var flowType: FlowType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConsoleBinding.inflate(layoutInflater)

        setContentView(binding.root)

        binding.telemetryConsole.movementMethod = ScrollingMovementMethod()

        binding.consoleCloseBtn.setOnClickListener {
            finish()
        }

        binding.consoleScrollDown.setOnClickListener {
            binding.telemetryConsole.scrollToEnd()
        }

        binding.consoleClearBtn.setOnClickListener {
            binding.telemetryConsole.clear()
        }

        with (intent) {
            deviceId = getStringExtra(PARAM_DEVICE_ID)
            flowType = getStringExtra(PARAM_FLOW_TYPE)?.let {
                FlowType.getByNameOrNull(it)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        bindService()
    }

    override fun onStop() {
        super.onStop()

        unbindService()
    }

    override fun onUserLeaveHint() {
        if (hasFeaturePIP) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration?) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        binding.consoleControlGroup.isVisible = !isInPictureInPictureMode
    }

    private fun bindService() {
        Intent(this, ScoreboardService::class.java).run {
            startForegroundService(this)
            bindService(this, connection, BIND_IMPORTANT)
        }
    }

    private fun unbindService() {
        connectionJob?.cancel()
        unbindService(connection)
        serviceBinder = null
    }

    private fun String.colorize(prefixLength: Int, color: Int) = SpannableString(this).apply {
        setSpan(ForegroundColorSpan(Color.WHITE), 0, prefixLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(ForegroundColorSpan(color), prefixLength, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = (service as? ScoreboardService.ScoreboardBinding).also {
                serviceBinder = it
            } ?: return

            with(binder) {
                connectionJob = lifecycleScope.launch {
                    binding.telemetryConsole.clear()

                    val flow = when (flowType) {
                        FlowType.ERROR -> {
                            val flow = deviceId?.let { getDeviceErrorFlow(it) }
                                ?: getGlobalErrorFlow()?.map { (_, event) -> event }
                            flow?.mapNotNull { event ->
                                (event.payload as? ErrorData)?.message?.inCommandFormat()
                                    ?: event.toInCommandFormat()
                            }
                        }

                        FlowType.ALL,
                        null -> getBacklogFlow()
                    }

                    flow?.collect { log ->
                        binding.telemetryConsole.writeLn(
                            log.colorize(COMMAND_DATE_PREFIX, if (log.startsWith('>')) Color.GREEN else Color.RED)
                        )
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connectionJob?.cancel()
            serviceBinder = null
            finish()
        }
    }

    companion object {
        private const val PARAM_DEVICE_ID = "param_device_id"
        private const val PARAM_FLOW_TYPE = "param_flow_type"

        private const val COMMAND_DATE_PREFIX = "XX $COMMAND_DATE_FORMAT : ".length

        fun startForDeviceFlow(context: Context, flowType: FlowType, deviceId: String? = null) {
            context.startActivity(Intent(context, ConsoleActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(PARAM_DEVICE_ID, deviceId)
                putExtra(PARAM_FLOW_TYPE, FlowType.ERROR.name)
            })
        }
    }
}