package com.nextgenbroadcast.mobile.middleware.sample

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionResolver(
    private val activity: Activity
) {
    private val prefs: Prefs by lazy {
        Prefs(activity.applicationContext)
    }

    private val notificationManager: NotificationManager by lazy {
        activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private var receiver: BroadcastReceiver? = null

    fun checkSelfPermission(): Boolean {
        val needsPermission = mutableListOf<String>()
        (necessaryPermissions + optionalPermissions).forEach { permission ->
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                needsPermission.add(permission)
            }
        }

        if (needsPermission.isNotEmpty()) {
            requestPermissions(needsPermission, PERMISSION_REQUEST_FIRST)
            return false
        }

        checkDNDPolicyAccess()

        return true
    }

    fun processPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        // permissions could empty if permission request was interrupted
        if ((requestCode == PERMISSION_REQUEST_FIRST
                    || requestCode == PERMISSION_REQUEST_SECOND) && permissions.isNotEmpty()) {
            val requiredPermissions = mutableListOf<String>()
            permissions.forEachIndexed { i, permission ->
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    requiredPermissions.add(permission)
                }
            }

            if (requiredPermissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(activity, activity.getString(R.string.warning_external_stortage_permission), Toast.LENGTH_LONG).show()
            }

            if (requiredPermissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)
                || requiredPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(activity, activity.getString(R.string.warning_access_background_location_permission), Toast.LENGTH_LONG).show()
            }

            // Ignore optional permissions
            requiredPermissions.removeAll(optionalPermissions - necessaryPermissions)

            if (requiredPermissions.isNotEmpty()) {
                if (requestCode == PERMISSION_REQUEST_FIRST) {
                    requestPermissions(requiredPermissions, PERMISSION_REQUEST_SECOND)
                }
            } else {
                checkDNDPolicyAccess()
                return true
            }
        }

        return false
    }

    private fun checkDNDPolicyAccess() {
        receiver?.let {
            try {
                activity.unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                // ignore
            }
            receiver = null
        }

        with(prefs) {
            if (!isNotificationPolicyAccessRequested
                || isNotificationPolicyAccessGranted && !notificationManager.isNotificationPolicyAccessGranted
            ) {
                isNotificationPolicyAccessRequested = true
                isNotificationPolicyAccessGranted = false

                receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED) {
                            isNotificationPolicyAccessGranted = notificationManager.isNotificationPolicyAccessGranted
                        }
                    }
                }.also {
                    activity.registerReceiver(it, IntentFilter().apply {
                        addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
                    })
                }

                showInfoDialog(R.string.permission_request_info_title, R.string.permission_request_info_dnd) {
                    activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
        }
    }

    private fun requestPermissions(needsPermission: List<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, needsPermission.toTypedArray(), requestCode)
    }

    private fun showInfoDialog(@StringRes titleResId: Int, @StringRes messageResId: Int, block: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(titleResId))
            .setMessage(activity.getString(messageResId))
            .setCancelable(true)
            .setNeutralButton(activity.getString(R.string.ok)) { _, _ ->
                block()
            }
            .show()
    }

    companion object {
        private const val PERMISSION_REQUEST_FIRST = 1000
        private const val PERMISSION_REQUEST_SECOND = 1001

        private val necessaryPermissions = listOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        private val optionalPermissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}