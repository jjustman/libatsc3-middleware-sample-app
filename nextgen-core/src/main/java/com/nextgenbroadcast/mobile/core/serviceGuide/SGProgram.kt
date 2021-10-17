package com.nextgenbroadcast.mobile.core.serviceGuide

data class SGProgram(
        val startTime: Long,
        val endTime: Long,
        val duration: Int,
        val content: SGProgramContent?
)