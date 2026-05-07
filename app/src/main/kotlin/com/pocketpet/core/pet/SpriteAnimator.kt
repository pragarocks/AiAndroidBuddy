package com.pocketpet.core.pet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

class SpriteAnimator(
    private val data: PetAnimationData,
    private val spritesheet: Bitmap?
) {
    private var currentState: AnimationState = data.states["idle"]!!
    private var currentFrame: Int = 0
    private var lastFrameTimeMs: Long = 0L

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val placeholderColors = mapOf(
        "idle" to 0xFF6EC6FF.toInt(),
        "excited" to 0xFFFFD740.toInt(),
        "working" to 0xFF69F0AE.toInt(),
        "thinking" to 0xFFE040FB.toInt(),
        "success" to 0xFF64FFDA.toInt(),
        "error" to 0xFFFF5252.toInt(),
        "waiting" to 0xFFFFAB40.toInt(),
        "sleeping" to 0xFF607D8B.toInt(),
        "running" to 0xFF40C4FF.toInt()
    )

    fun setState(stateKey: String) {
        val newState = data.states[stateKey] ?: data.states["idle"] ?: return
        if (newState != currentState) {
            currentState = newState
            currentFrame = 0
            lastFrameTimeMs = 0L
        }
    }

    fun update(nowMs: Long) {
        val frameDurationMs = 1000L / currentState.fps.coerceAtLeast(1)
        if (nowMs - lastFrameTimeMs >= frameDurationMs) {
            currentFrame = (currentFrame + 1) % currentState.frames.coerceAtLeast(1)
            lastFrameTimeMs = nowMs
        }
    }

    fun draw(canvas: Canvas, destRect: Rect) {
        if (spritesheet != null) {
            drawFromSpritesheet(canvas, destRect)
        } else {
            drawPlaceholder(canvas, destRect)
        }
    }

    private fun drawFromSpritesheet(canvas: Canvas, destRect: Rect) {
        val fw = data.frameSize.width
        val fh = data.frameSize.height
        val srcRect = Rect(
            currentFrame * fw,
            currentState.row * fh,
            (currentFrame + 1) * fw,
            (currentState.row + 1) * fh
        )
        canvas.drawBitmap(spritesheet!!, srcRect, destRect, framePaint)
    }

    private fun drawPlaceholder(canvas: Canvas, destRect: Rect) {
        val stateKey = data.states.entries.find { it.value == currentState }?.key ?: "idle"
        val color = placeholderColors[stateKey] ?: 0xFF6EC6FF.toInt()

        placeholderPaint.color = color
        val radius = (destRect.width() / 2).toFloat()
        canvas.drawCircle(
            destRect.exactCenterX(),
            destRect.exactCenterY(),
            radius * 0.85f,
            placeholderPaint
        )

        placeholderPaint.color = 0xFF000000.toInt()
        placeholderPaint.textSize = radius * 0.4f
        placeholderPaint.textAlign = android.graphics.Paint.Align.CENTER
        val emoji = stateEmoji(stateKey)
        canvas.drawText(emoji, destRect.exactCenterX(), destRect.exactCenterY() + radius * 0.15f, placeholderPaint)
    }

    private fun stateEmoji(state: String) = when (state) {
        "idle" -> "😊"
        "excited" -> "🤩"
        "working" -> "💪"
        "thinking" -> "🤔"
        "success" -> "✨"
        "error" -> "😱"
        "waiting" -> "👂"
        "sleeping" -> "😴"
        "running" -> "🏃"
        else -> "😊"
    }
}
