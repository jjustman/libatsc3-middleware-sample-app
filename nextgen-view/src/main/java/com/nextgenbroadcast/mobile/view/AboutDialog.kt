package com.nextgenbroadcast.mobile.view

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment

class AboutDialog(
    private val sdkVersion: String?,
    private val firmwareVersion: String?,
    private val deviceType: String?,
    private val frequency: Int?,
    private val deviceId: String?
) : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setStyle(STYLE_NORMAL, R.style.Dialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_about, container, false)

        view.findViewById<View>(R.id.but_ok_about).setOnClickListener {
            dismiss()
        }

        view.findViewById<TextView>(R.id.tv_about_content).text = getInfo()

        return view
    }

    @SuppressLint("MissingPermission")
    private fun getInfo(): CharSequence {
        val context = requireContext()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        val stringBuilder = StringBuilder().apply {
            packageInfo?.versionName?.let {
                appendBoldTitle(R.string.version_name).append(it)
            }

            packageInfo?.longVersionCode?.let {
                if (isNotEmpty()) {
                    append(DOUBLE_LINE_BREAK)
                }
                appendBoldTitle(R.string.version_code).append(it)
            }

            try {
                Build.getSerial()?.let {
                    if (isNotEmpty()) {
                        append(DOUBLE_LINE_BREAK)
                    }
                    appendBoldTitle(R.string.android_id).append(it)
                }
            } catch (e: SecurityException) {
            }

            append(DOUBLE_LINE_BREAK)
            appendBoldTitle(R.string.phy_sdk_version).append(sdkVersion.orDash())

            append(DOUBLE_LINE_BREAK)
            appendBoldTitle(R.string.firmware_version).append(firmwareVersion.orDash())

            deviceType?.let {
                append(DOUBLE_LINE_BREAK)
                appendBoldTitle(R.string.deviceType).append(deviceType)
            }

            append(DOUBLE_LINE_BREAK)
            appendBoldTitle(R.string.deviceId).append(deviceId.orDash())

            append(DOUBLE_LINE_BREAK)
            appendBoldTitle(R.string.frequency).append(frequency ?: 0)
        }

        return Html.fromHtml(stringBuilder.toString(), Html.FROM_HTML_MODE_LEGACY)
    }

    private fun StringBuilder.appendBoldTitle(@StringRes resId: Int) = appendBoldTitle(getString(resId))

    private fun StringBuilder.appendBoldTitle(title: String): StringBuilder {
        append("<b>").append(title).append(" ").append("</b>")
        return this
    }

    private fun String?.orDash() = this ?: "-"

    companion object {
        private const val DOUBLE_LINE_BREAK = "<br><br>"
    }
}