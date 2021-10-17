package com.nextgenbroadcast.mobile.core.exception

import java.io.FileNotFoundException

class ServiceNotFoundException(
    reason: String
) : FileNotFoundException(reason)