package com.nextgenbroadcast.mobile.middleware.scoreboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.max

class ConsoleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : androidx.appcompat.widget.AppCompatTextView(context, attrs) {
    private val buffer = SpannableStringBuilder()

    private var isUserTouching: Boolean = false
    private var touchY: Float = 0f
    private var lockAutoScroll = false

    fun writeLn(sequence: CharSequence) {
        buffer.append(sequence).append("\n")
        ensureSize()
        text = buffer

        if (!lockAutoScroll && !isUserTouching) {
            scrollDown()
        }
    }

    fun clear() {
        buffer.clear()
        text = ""
        scrollToEnd()
    }

    fun scrollToEnd() {
        lockAutoScroll = false
        scrollDown()
    }

    private fun scrollDown() {
        val range = computeVerticalScrollRange() - computeVerticalScrollExtent()
        scrollTo(0, max(range, 0))
    }

    private fun ensureSize() {
        if (buffer.length > MAX_BUFFER_SIZE) {
            buffer.replace(0, buffer.length - MAX_BUFFER_SIZE, "")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                isUserTouching = true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (isUserTouching) {
                    if (abs(touchY - event.rawY) > DRAG_THRESHOLD) {
                        val offset = computeVerticalScrollOffset()
                        val range = computeVerticalScrollRange() - computeVerticalScrollExtent()
                        lockAutoScroll = (offset != max(range, 0))
                    }
                }
                isUserTouching = false
            }
        }

        return super.onTouchEvent(event)
    }

    companion object {
        private const val DRAG_THRESHOLD = 100
        private const val MAX_BUFFER_SIZE = 64 * 1024
    }
}