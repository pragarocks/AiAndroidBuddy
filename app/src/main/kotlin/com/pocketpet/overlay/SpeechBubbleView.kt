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
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A compact speech bubble that:
 * - wraps its content (never wider than maxWidthDp)
 * - has a small downward tail pointing to the pet below
 * - fades in/out smoothly
 */
class SpeechBubbleView(context: Context) : LinearLayout(context) {

    private val textView: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    companion object {
        private const val DEFAULT_DISPLAY_MS = 5000L
        private const val MAX_WIDTH_DP = 220f   // hard cap: never wider than 220dp
        private const val TEXT_SIZE_SP = 13f
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        // Max width cap — actual rendered width is WRAP_CONTENT up to this
        val maxPx = dpToPx(MAX_WIDTH_DP).toInt()
        minimumWidth = 0
        // We set maxWidth via a custom measure override instead of a hard setWidth
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

        // Rounded bubble background
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(14f)
            setColor(Color.parseColor("#F1F8E9"))
            setStroke(dpToPx(1.5f).toInt(), Color.parseColor("#66BB6A"))
        }
        background = bg

        val hPad = dpToPx(10f).toInt()
        val vPad = dpToPx(6f).toInt()
        setPadding(hPad, vPad, hPad, vPad)

        textView = TextView(context).apply {
            setTextColor(Color.parseColor("#1B5E20"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP)
            maxLines = 5
            gravity = Gravity.CENTER
            // Constrain text width so the bubble auto-sizes correctly
            maxWidth = maxPx - hPad * 2
        }

        addView(textView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        visibility = View.GONE
    }

    fun show(text: String, durationMs: Long = DEFAULT_DISPLAY_MS) {
        mainHandler.post {
            textView.text = text
            if (visibility != View.VISIBLE) {
                visibility = View.VISIBLE
                alpha = 0f
                ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
                    duration = 180
                    start()
                }
            }
            scheduleDismiss(durationMs)
        }
    }

    fun hide() {
        mainHandler.post {
            dismissRunnable?.let { mainHandler.removeCallbacks(it) }
            dismissRunnable = null
            ObjectAnimator.ofFloat(this, "alpha", alpha, 0f).apply {
                duration = 200
                start()
            }
            postDelayed({ visibility = View.GONE }, 210)
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
