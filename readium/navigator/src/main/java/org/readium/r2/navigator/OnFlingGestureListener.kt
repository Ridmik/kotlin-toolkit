package org.readium.r2.navigator

import android.util.Log
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import kotlin.math.abs
import org.readium.r2.shared.InternalReadiumApi
import timber.log.Timber

internal class OnFlingGestureListener(
    internal var listener: R2BasicWebView.Listener?
) : SimpleOnGestureListener() {

    private var isAtTop = false
    private var isAtBottom = false

    val isAtEdge get() = isAtTop || isAtBottom

    fun setTopBottom(isTop: Boolean, isBottom: Boolean) {
        isAtTop = isTop
        isAtBottom = isBottom
    }



    @OptIn(InternalReadiumApi::class)
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        val diffY = e2.y - (e1?.y ?:0f)

        if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
            if (diffY > 0) {
                // Swipe down
                if (isAtTop) {
                    Timber.tag("Gesture").d("Swipe down detected at the top! ⬇️");
                    listener?.let { l ->
                        l.goBackward(true)
                        l.goToPreviousResource(jump = true, animated = true)
                    }
                }
            } else {
                // Swipe up
                if (isAtBottom) {
                    Timber.tag("Gesture").d("Swipe up detected at the bottom! ⬆️");
                    listener?.let { l ->
                        l.goForward(true)
                        l.goToNextResource(jump = true, animated = true)
                    }
                }
            }
            setTopBottom(isTop = false, isBottom = false)
            return true
        }
        else {
            return super.onFling(e1, e2, velocityX, velocityY)
        }
    }

    public companion object {
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }
}
