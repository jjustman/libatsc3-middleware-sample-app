package com.nextgenbroadcast.mobile.middleware.sample.core

import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

abstract class SwipeGestureDetector: GestureDetector.SimpleOnGestureListener() {

    override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        if (e1.x - e2.x > OFFSET_THRESHOLD && abs(velocityX) > VELOCITY_THRESHOLD) {
            onClose()
        } else if (e2.x - e1.x > OFFSET_THRESHOLD && abs(velocityX) > VELOCITY_THRESHOLD) {
            onOpen()
        }

        return super.onFling(e1, e2, velocityX, velocityY)
    }

    abstract fun onClose()
    abstract fun onOpen()

    companion object {
        private const val OFFSET_THRESHOLD = 100
        private const val VELOCITY_THRESHOLD = 800
    }
}