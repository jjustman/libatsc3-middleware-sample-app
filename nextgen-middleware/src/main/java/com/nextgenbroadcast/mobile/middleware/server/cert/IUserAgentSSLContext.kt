package com.nextgenbroadcast.mobile.middleware.server.cert

import java.io.IOException
import java.security.GeneralSecurityException
import javax.net.ssl.SSLContext

interface IUserAgentSSLContext {
    @Throws(GeneralSecurityException::class, IOException::class)
    fun getInitializedSSLContext(password: String): SSLContext

    fun getCertificateHash(): String?
}