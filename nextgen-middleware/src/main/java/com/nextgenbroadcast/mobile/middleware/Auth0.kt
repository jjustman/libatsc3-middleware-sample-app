package com.nextgenbroadcast.mobile.middleware

object Auth0 {
    init {
        System.loadLibrary("middleware")
    }

    external fun username(): String
    external fun password(): String
    external fun clientId(): String
    external fun clientSecret(): String
    external fun clientKey(): String
}