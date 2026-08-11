package nl.psalmbladmuziek.app

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.webkit.WebView

class SheetGestureController(
    context: Context,
    private val onPreviousVerse: () -> Unit,
    private val onNextVerse: () -> Unit,
    private val onTextDoubleTap: () -> Unit,
    private val onPinch: (Double) -> Unit,
    private val onPinchEnd: () -> Unit
) {
    private val swipeDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (Math.abs(dx) > Math.abs(dy) * 1.5f && Math.abs(dx) > 150f && Math.abs(vx) > 250f) {
                if (dx > 0) onPreviousVerse() else onNextVerse()
                return true
            }
            return false
        }
    })

    private val doubleTapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            onTextDoubleTap()
            return true
        }
    })

    private val pinchDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            onPinch(detector.scaleFactor.toDouble())
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            onPinchEnd()
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    fun attach(webView: WebView, lyricsScrollView: View) {
        val touch = View.OnTouchListener { view, event ->
            pinchDetector.onTouchEvent(event)
            if (!pinchDetector.isInProgress) {
                swipeDetector.onTouchEvent(event)
                if (view.id == lyricsScrollView.id) {
                    doubleTapDetector.onTouchEvent(event)
                }
            }
            false
        }
        webView.setOnTouchListener(touch)
        lyricsScrollView.setOnTouchListener(touch)
    }
}
