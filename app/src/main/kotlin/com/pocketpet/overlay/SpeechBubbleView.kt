package com.pocketpet.overlay

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

class SpeechBubbleView(context: Context) : FrameLayout(context) {

    private val textView: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    companion object {
        const val MAX_CHARS = 120
        private const val DEFAULT_DISPLAY_MS = 5000L
    }

    init {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(16f)
            setColor(Color.parseColor("#E8F5E9"))
            setStroke(dpToPx(2f).toInt(), Color.parseColor("#4CAF50"))
        }
        background = bg

        val paddingPx = dpToPx(12f).toInt()
        setPadding(paddingPx, paddingPx / 2, paddingPx, paddingPx / 2)

        textView = TextView(context).apply {
            setTextColor(Color.parseColor("#1B5E20"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 4
            gravity = Gravity.START
        }
        addView(textView, LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        visibility = View.GONE
    }

    fun show(text: String, durationMs: Long = DEFAULT_DISPLAY_MS) {
        val truncated = text.take(MAX_CHARS)
        mainHandler.post {
            textView.text = truncated
            visibility = View.VISIBLE
            alpha = 0f
            ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
                duration = 200
                start()
            }
            scheduleDismiss(durationMs)
        }
    }

    fun hide() {
        mainHandler.post {
            ObjectAnimator.ofFloat(this, "alpha", 1f, 0f).apply {
                duration = 200
                start()
            }
            postDelayed({ visibility = View.GONE }, 200)
        }
    }

    private fun scheduleDismiss(durationMs: Long) {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = Runnable { hide() }.also {
            mainHandler.postDelayed(it, durationMs)
        }
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
}
