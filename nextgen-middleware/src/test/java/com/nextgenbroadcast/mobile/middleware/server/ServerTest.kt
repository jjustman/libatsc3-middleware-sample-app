package com.nextgenbroadcast.mobile.middleware.server

import android.content.Context
import android.content.res.Resources
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nextgenbroadcast.mobile.core.cert.CertificateUtils
import com.nextgenbroadcast.mobile.middleware.server.cert.UserAgentSSLContext
import com.nextgenbroadcast.mobile.middleware.gateway.web.ConnectionType
import com.nextgenbroadcast.mobile.middleware.server.web.configureSSLFactory
import com.nextgenbroadcast.mobile.middleware.server.web.getSecureServerConnector
import com.nextgenbroadcast.mobile.middleware.server.web.getServerConnector
import org.eclipse.jetty.server.Server
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.core.classloader.annotations.PrepareForTest
import javax.net.ssl.KeyManagerFactory

@PrepareForTest(CertificateUtils::class, java.lang.String::class)
@PowerMockIgnore("javax.net.ssl.*", "javax.security.*")
open class ServerTest {
    @Rule
    @ClassRule
    @JvmField
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    lateinit var mockApplicationContext: Context

    @Mock
    lateinit var mockContextResources: Resources

    lateinit var server: Server

    @Before
    fun setupContext() {
        Mockito.`when`(mockApplicationContext.resources).thenReturn(mockContextResources)
        Mockito.`when`(mockContextResources.openRawResource(ArgumentMatchers.anyInt())).then {
            javaClass.getResourceAsStream("mykey.p12")
        }

        PowerMockito.mockStatic(CertificateUtils::class.java)
        Mockito.`when`(CertificateUtils.CERTIFICATE_ALGORITHM).thenReturn(KeyManagerFactory.getDefaultAlgorithm())

        server = Server()

        val sslContextFactory = configureSSLFactory(UserAgentSSLContext.newInstance(mockApplicationContext))
        with(server) {
            connectors = arrayListOf(
                    getServerConnector(ConnectionType.HTTP, this, HOST_NAME, HTTP_PORT),
                    getServerConnector(ConnectionType.WS, this, HOST_NAME, WS_PORT),
                    getSecureServerConnector(ConnectionType.HTTPS, this, HOST_NAME, HTTPS_PORT, sslContextFactory),
                    getSecureServerConnector(ConnectionType.WSS, this, HOST_NAME, WSS_PORT, sslContextFactory)
            ).toTypedArray()
        }
    }
    
    companion object {
        const val HTTP_PORT = 8081
        const val HTTPS_PORT = 8443
        const val WS_PORT = 9998
        const val WSS_PORT = 9999
        const val HOST_NAME = "localhost"
    }
}