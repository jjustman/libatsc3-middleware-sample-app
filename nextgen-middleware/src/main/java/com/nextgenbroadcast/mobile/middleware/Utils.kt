package com.nextgenbroadcast.mobile.middleware

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nextgenbroadcast.mobile.core.LOG

internal fun encryptedSharedPreferences(context: Context, fileName: String): SharedPreferences {
    val appContext = context.applicationContext
    return EncryptedSharedPreferences.create(
            appContext,
            fileName,
            MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

internal fun startTVApplication(context: Context) {
    try {
        val intent = Intent(context.getString(R.string.defaultActionWatch)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ContextCompat.startActivity(
            context,
            Intent.createChooser(intent, context.getString(R.string.tv_application_selection_title))
                .apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            null
        )
    } catch (e: ActivityNotFoundException) {
        LOG.i("startTVApplication", "Unable to start TV application", e)
    }
}