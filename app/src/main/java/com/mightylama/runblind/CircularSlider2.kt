package com.mightylama.runblind

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import me.angrybyte.circularslider.CircularSlider

class CircularSlider2(context: Context, attributeSet: AttributeSet) : CircularSlider(context, attributeSet) {

    private var trackedPosition = 0.0
    private var onStopTrackingListener: ((Double) -> Unit)? = null
    private var onStartTrackingListener: ((Double) -> Unit)? = null

    init {
        this.setOnSliderMovedListener(null)
    }

    fun setOnStartTrackingListener(listener: ((Double) -> Unit)?) {
        onStartTrackingListener = listener
    }

    fun setOnStopTrackingListener(listener: ((Double) -> Unit)?) {
        onStopTrackingListener = listener
    }

    override fun setPosition(pos: Double) {
        super.setPosition(pos)
        trackedPosition = pos
        invalidate()
    }

    override fun setOnSliderMovedListener(listener: OnSliderMovedListener?) {
        val newListener = OnSliderMovedListener { pos: Double ->
            listener?.onSliderMoved(pos)
            trackedPosition = pos
            if (trackedPosition < 0) trackedPosition += 1
        }

        super.setOnSliderMovedListener(newListener)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean {

        when(ev?.action) {
            MotionEvent.ACTION_UP -> { onStopTrackingListener?.let { it(trackedPosition) }}
            MotionEvent.ACTION_DOWN -> {onStartTrackingListener?.let { it(trackedPosition) }}
        }

        return super.onTouchEvent(ev)
    }
}