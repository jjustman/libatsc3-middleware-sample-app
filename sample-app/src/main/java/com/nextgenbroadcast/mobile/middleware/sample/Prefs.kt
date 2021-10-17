package com.nextgenbroadcast.mobile.middleware.sample

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class Prefs(
    context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
    }

    var isNotificationPolicyAccessRequested: Boolean
        get() {
            return prefs.ignoreClassCastException {
                getBoolean(NOTIFICATION_POLICY_REQUESTED, false)
            } ?: false
        }
        set(value) {
            prefs.edit {
                putBoolean(NOTIFICATION_POLICY_REQUESTED, value)
            }
        }

    var isNotificationPolicyAccessGranted: Boolean
        get() {
            return prefs.ignoreClassCastException {
                getBoolean(NOTIFICATION_POLICY_GRANTED, false)
            } ?: false
        }
        set(value) {
            prefs.edit {
                putBoolean(NOTIFICATION_POLICY_GRANTED, value)
            }
        }

    private inline fun <T, R> T.ignoreClassCastException(block: T.() -> R?): R? {
        return try {
            block(this)
        } catch (e: ClassCastException) {
            null
        }
    }

    companion object {
        private const val PREFERENCE_FILE_NAME = "${BuildConfig.APPLICATION_ID}.prefs"

        private const val NOTIFICATION_POLICY_REQUESTED = "notification_policy_requested"
        private const val NOTIFICATION_POLICY_GRANTED = "notification_policy_granted"
    }
}