package com.nextgenbroadcast.mobile.middleware.server.cert

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.nextgenbroadcast.mobile.core.cert.CertificateUtils
import com.nextgenbroadcast.mobile.core.cert.CertificateUtils.publicHash
import com.nextgenbroadcast.mobile.core.cert.CertificateUtils.toPrivateKey
import com.nextgenbroadcast.mobile.core.cert.CertificateUtils.toX509Certificate
import com.nextgenbroadcast.mobile.middleware.BuildConfig
import com.nextgenbroadcast.mobile.middleware.encryptedSharedPreferences
import java.io.IOException
import java.security.*
import java.security.cert.Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class UserAgentSSLContext(
        private val preferences: SharedPreferences
) : IUserAgentSSLContext {

    @Throws(GeneralSecurityException::class, IOException::class)
    override fun getInitializedSSLContext(password: String): SSLContext {
        val keystore = loadKeystore(password)
        val keyManagerFactory = KeyManagerFactory.getInstance(CertificateUtils.CERTIFICATE_ALGORITHM).apply {
            init(keystore, password.toCharArray())
        }
        val tmf = TrustManagerFactory.getInstance(CertificateUtils.CERTIFICATE_ALGORITHM).apply {
            init(keystore)
        }

        return SSLContext.getInstance("TLS").apply {
            init(keyManagerFactory.keyManagers, tmf.trustManagers, null)
        }
    }

    private fun loadKeystore(password: String): KeyStore {
        val certificatePem = preferences.getString(PREF_CERTIFICATE_PEM, null)
        val privateKeyStr = preferences.getString(PREF_CERTIFICATE_KEY, null)
        return if (certificatePem != null && privateKeyStr != null) {
            val privateKey = keyFromString(privateKeyStr)
            val certificate = certFromString(certificatePem)
            createKeyStore(privateKey, password, certificate)
        } else {
            CertificateUtils.genCertificate(ISSUER)?.let { (privateKey, certificate) ->
                preferences.edit()
                        .putString(PREF_CERTIFICATE_PEM, certToString(certificate))
                        .putString(PREF_CERTIFICATE_KEY, keyToString(privateKey))
                        .apply()

                createKeyStore(privateKey, password, certificate)
            } ?: let {
                throw IOException("Can't reade User Agent certificate")
            }
        }
    }

    private fun createKeyStore(privateKey: PrivateKey, password: String, certificate: Certificate): KeyStore {
        return KeyStore.getInstance(KEY_STORE_TYPE).apply {
            load(null)
            setKeyEntry(KEY_STORE_ALIAS,
                    privateKey as Key,
                    password.toCharArray(),
                    arrayOf(certificate)
            )
        }
    }

    override fun getCertificateHash(): String? {
        return preferences.getString(PREF_CERTIFICATE_PEM, null)?.let { certificatePem ->
            certFromString(certificatePem).publicHash()
        }
    }

    private fun certToString(certificate: Certificate) = certificate.encoded.encodeBase64()

    private fun certFromString(base64: String): Certificate {
        return base64.decodeBase64().toX509Certificate()
    }

    private fun keyToString(key: PrivateKey) = key.encoded.encodeBase64()

    private fun keyFromString(base64: String): PrivateKey {
        return base64.decodeBase64().toPrivateKey()
    }

    private fun ByteArray.encodeBase64(): String {
        return Base64.encodeToString(this, Base64.DEFAULT)
    }

    private fun String.decodeBase64(): ByteArray {
        return Base64.decode(this, Base64.DEFAULT)
    }

    companion object {
        const val KEY_STORE_TYPE = "BKS"
        const val KEY_STORE_ALIAS = "key"

        private const val ISSUER = "middleware-cert"
        private const val PREF_CERTIFICATE_PEM = "certificatePem"
        private const val PREF_CERTIFICATE_KEY = "privateKey"

        private const val CERT_PREFERENCE = "${BuildConfig.LIBRARY_PACKAGE_NAME}.webserver"

        fun newInstance(context: Context): IUserAgentSSLContext {
            return UserAgentSSLContext(
                    encryptedSharedPreferences(context.applicationContext, CERT_PREFERENCE)
            )
        }
    }
}