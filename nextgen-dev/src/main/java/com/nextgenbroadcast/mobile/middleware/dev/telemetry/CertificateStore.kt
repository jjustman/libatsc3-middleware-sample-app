package com.nextgenbroadcast.mobile.middleware.dev.telemetry

import android.content.Context
import com.nextgenbroadcast.mobile.core.cert.CertificateUtils

object CertificateStore {
    fun getReceiverCert(context: Context, keyPassword: String) = CertificateUtils.createKeyStore(
        context.assets.open("9200fd27be-certificate.pem.crt"),
        context.assets.open("9200fd27be-private.pem.key"),
        keyPassword
    )

    fun getManagerCert(context: Context, keyPassword: String) = CertificateUtils.createKeyStore(
        context.assets.open("cc74370283-certificate.pem.crt"),
        context.assets.open("cc74370283-private.pem.key"),
        keyPassword
    )
}