package com.nextgenbroadcast.mobile.middleware.net

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun Call.await(): Response = await { it }

@Throws(IOException::class)
suspend fun <T> Call.await(action: (Response) -> T): T {
    return suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    cont.resumeWithException(IOException("Unexpected HTTP code: ${response.code}"))
                    return
                }

                try {
                    cont.resume(action.invoke(response))
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }
        })
    }
}