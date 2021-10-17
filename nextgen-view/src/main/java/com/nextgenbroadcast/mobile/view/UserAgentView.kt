package com.nextgenbroadcast.mobile.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.util.AttributeSet
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged
import com.nextgenbroadcast.mobile.core.atsc3.Atsc3Config
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.core.MiddlewareConfig
import com.nextgenbroadcast.mobile.core.cert.CertificateUtils.publicHash
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import kotlin.math.max

class UserAgentView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    interface IListener {
        fun onOpened() {}
        fun onClosed() {}
        fun onLoadingError() {}
        fun onLoadingSuccess() {}
    }

    private enum class State {
        IDLE, LOADING, FINISHED
    }

    private var listener: IListener? = null

    private var entryPointList: List<String> = emptyList()
    private var appEntryPoint: String? = null
    private var loadingRetryCount: Int = 0
    private var reloadRunnable: Runnable? = null
    private var state = State.IDLE

    // content visibility hack
    private var _isContentVisible = MutableLiveData<Boolean>()
    private var layerBitmap: Bitmap? = null
    private var emptyBitmap: Bitmap? = null
    private var layerCanvas: Canvas? = null
    private var lastCaptureTime: Long = 0

    val isContentLoaded: Boolean
        get() = state == State.FINISHED

    var serverCertificateHash: List<String> = emptyList()
    var captureContentVisibility = false
    var isContentVisible: LiveData<Boolean> = _isContentVisible.distinctUntilChanged()

    fun setListener(listener: IListener) {
        this.listener = listener
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onFinishInflate() {
        super.onFinishInflate()

        if (isInEditMode) return

        clearCache(true)
        setBackgroundColor(Color.TRANSPARENT)
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = getAtsc3UserAgent()
        }
        if (MiddlewareConfig.DEV_TOOLS) {
            setWebContentsDebuggingEnabled(true)
        }
        clearSslPreferences()
        webViewClient = createWebViewClient()
    }

    fun checkContentVisible(): Boolean {
        if (visibility != View.VISIBLE || width == 0 || height == 0 || alpha == 0f) return false

        val captureContent = captureContentVisibility
        captureContentVisibility = false
        draw(getCaptureCanvas())
        captureContentVisibility = captureContent

        return !isCaptureEmpty()
    }

    private fun getAtsc3UserAgent(): String {
        val defaultUserAgent = WebSettings.getDefaultUserAgent(context)
        val atsc3Date = "${Atsc3Config.A300_YEAR}-${Atsc3Config.A300_MONTH}"
        val capabilities = String.format("%04X", Atsc3Config.CAPABILITIES)
        return "ATSC3/$atsc3Date ($capabilities) $defaultUserAgent"
    }

    private fun isCaptureEmpty(): Boolean {
        return layerBitmap?.let { layerBmp ->
            emptyBitmap?.let { emptyBmp ->
                layerBmp.sameAs(emptyBmp)
            }
        } ?: false
    }

    private fun getCaptureCanvas(): Canvas {
        val captureWidth = max(width / 2, 1)
        val captureHeight = max(height, 1)

        val layerBmp = layerBitmap?.let { bmp ->
            if (bmp.width != captureWidth || bmp.height != captureHeight) {
                createLayerBitmap(captureWidth, captureHeight)
            } else bmp.also {
                it.eraseColor(Color.TRANSPARENT)
            }
        } ?: createLayerBitmap(captureWidth, captureHeight)

        emptyBitmap?.let { bmp ->
            if (bmp.width != captureWidth || bmp.height != captureHeight) {
                createEmptyBitmap(captureWidth, captureHeight)
            } else bmp
        } ?: createEmptyBitmap(captureWidth, captureHeight)

        return layerCanvas ?: Canvas(layerBmp).also {
            layerCanvas = it
        }
    }

    private fun createBitmap(w: Int, h: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)

    private fun createLayerBitmap(w: Int, h: Int) = createBitmap(w, h).also { bmp ->
        layerBitmap = bmp
        layerCanvas = null
    }

    private fun createEmptyBitmap(w: Int, h: Int) = createBitmap(w, h).also { bmp ->
        emptyBitmap = bmp
    }

    private val captureContentTask = Runnable {
        _isContentVisible.value = checkContentVisible()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (captureContentVisibility) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCaptureTime > CAPTURE_PERIOD_MILS) {
                lastCaptureTime = currentTime

                super.onDraw(getCaptureCanvas())

                _isContentVisible.value = !isCaptureEmpty()

                removeCallbacks(captureContentTask)
                postDelayed(captureContentTask, CAPTURE_DELAY_MILS)
            }
        }
    }

    private fun createWebViewClient() = object : WebViewClient() {

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)

            if (state == State.LOADING && url == appEntryPoint) {
                state = State.FINISHED
                listener?.onLoadingSuccess()
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            LOG.d(TAG, "onReceivedSslError: $error")

            if (error.primaryError == SslError.SSL_IDMISMATCH || error.primaryError == SslError.SSL_UNTRUSTED) {
                val cert = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    error.certificate.x509Certificate
                } else {
                    getX509Certificate(error.certificate)
                }

                if (cert != null && serverCertificateHash.contains(cert.publicHash())) {
                    handler.proceed()
                    return
                }
            }

            handler.cancel()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError?) {
            super.onReceivedError(view, request, error)

            LOG.e(TAG, "onReceivedError request: ${request.url}, errorCode: ${error?.errorCode}, description: ${error?.description}")

            onLoadingError(request.url)
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse?) {
            super.onReceivedHttpError(view, request, errorResponse)

            LOG.e(TAG, "onReceivedHttpError request: ${request.url}, statusCode: ${errorResponse?.statusCode}, reason: ${errorResponse?.reasonPhrase}")

            onLoadingError(request.url)
        }

        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
            super.onReceivedClientCertRequest(view, request)

            LOG.d(TAG, "onReceivedClientCertRequest - certificate should be provided")

            //TODO: do we need this?
//            UserAgentSSLContext(view.context).loadCertificateAndPrivateKey()?.let { (privateKey, chain) ->
//                request.proceed(privateKey, arrayOf(chain))
//            }
        }
    }

    private fun onLoadingError(uri: Uri) {
        if (reloadRunnable != null) return

        val entryPoint = appEntryPoint
        if (uri.toString() == entryPoint) {
            state = State.IDLE

            loadBlankPage()

            if (loadingRetryCount < MAX_RETRY_COUNT) {
                loadingRetryCount++

                reloadRunnable = Runnable {
                    reloadRunnable = null
                    loadEntryPoint(entryPoint)
                }.also {
                    postDelayed(it, RETRY_DELAY_MILS * loadingRetryCount)
                }
            } else {
                getNextEntryPointOrNull()?.run {
                    loadingRetryCount = 0
                    loadEntryPoint(this)
                } ?: listener?.onLoadingError()
            }
        } else {
            listener?.onLoadingError()
        }
    }

    fun load(entryPoint: String) {
        loadFirstAvailable(listOf(entryPoint))
    }

    fun loadFirstAvailable(entryPoints: List<String>) {
        reset()
        entryPointList = entryPoints
        getNextEntryPointOrNull()?.run {
            loadEntryPoint(this)
        }
    }

    fun unload() {
        reset()
        loadBlankPage()
    }

    fun actionExit() {
        sendKeyPress(KeyEvent.KEYCODE_DPAD_LEFT, 105)

        listener?.onClosed()
    }

    fun actionEnter() {
        sendKeyPress(KeyEvent.KEYCODE_DPAD_RIGHT, 106)

        listener?.onOpened()
    }

    private fun loadBlankPage() {
        loadUrl("about:blank")
        _isContentVisible.value = false
    }

    private fun loadEntryPoint(entryPoint: String) {
        appEntryPoint = entryPoint
        state = State.LOADING
        loadUrl(entryPoint)
    }

    private fun getNextEntryPointOrNull() = with(entryPointList) {
        val entryPoint = appEntryPoint
        if (entryPoint != null) {
            getOrNull(indexOf(entryPoint) + 1)
        } else firstOrNull()
    }

    private fun reset() {
        removeCallbacks(reloadRunnable)
        reloadRunnable = null
        loadingRetryCount = 0
        appEntryPoint = null
        entryPointList = emptyList()
    }

    private fun sendKeyPress(keyCode: Int, scanCode: Int) {
        dispatchKeyEvent(keyEvent(KeyEvent.ACTION_DOWN, keyCode, scanCode))
        dispatchKeyEvent(keyEvent(KeyEvent.ACTION_UP, keyCode, scanCode))
    }

    private fun keyEvent(action: Int, keyCode: Int, scanCode: Int) = KeyEvent(
            0,
            0,
            action,
            keyCode,
            0,
            0,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            scanCode)

    fun getX509Certificate(sslCertificate: SslCertificate): Certificate? {
        val bundle = SslCertificate.saveState(sslCertificate)
        val bytes = bundle.getByteArray("x509-certificate")
        return if (bytes == null) {
            null
        } else {
            try {
                CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes))
            } catch (e: CertificateException) {
                null
            }
        }
    }

    companion object {
        val TAG: String = UserAgentView::class.java.simpleName

        private const val RETRY_DELAY_MILS = 500L
        private const val MAX_RETRY_COUNT = 1
        private const val CAPTURE_PERIOD_MILS = 200L
        private const val CAPTURE_DELAY_MILS = CAPTURE_PERIOD_MILS + 100L
    }
}