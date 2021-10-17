package com.nextgenbroadcast.mobile.core.dev

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

interface IWebInterface {
    fun addHandler(path: String, onGet: (req: HttpServletRequest, resp: HttpServletResponse) -> Unit): Boolean
    fun removeHandler(path: String)
}