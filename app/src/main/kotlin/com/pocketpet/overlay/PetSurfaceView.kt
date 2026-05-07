package com.pocketpet.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.pocketpet.core.pet.LoadedPet
import com.pocketpet.core.pet.SpriteAnimator

class PetSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val renderThread = HandlerThread("PetRenderThread").apply { start() }
    private val renderHandler = Handler(renderThread.looper)

    private var animator: SpriteAnimator? = null
    private var isRendering = false

    private val destRect = Rect()
    private val targetFps = 30
    private val frameIntervalMs = 1000L / targetFps

    init {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
    }

    fun loadPet(pet: LoadedPet) {
        animator = SpriteAnimator(pet.data, pet.spritesheet)
    }

    fun setState(stateKey: String) {
        animator?.setState(stateKey)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRendering = true
        scheduleFrame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        destRect.set(0, 0, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRendering = false
        renderHandler.removeCallbacksAndMessages(null)
    }

    private fun scheduleFrame() {
        if (!isRendering) return
        renderHandler.postDelayed({
            drawFrame()
            scheduleFrame()
        }, frameIntervalMs)
    }

    private fun drawFrame() {
        val surfaceHolder = holder
        var canvas: Canvas? = null
        try {
            canvas = surfaceHolder.lockCanvas(null) ?: return
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            val anim = animator ?: return
            anim.update(System.currentTimeMillis())
            if (!destRect.isEmpty) {
                anim.draw(canvas, destRect)
            }
        } finally {
            canvas?.let { surfaceHolder.unlockCanvasAndPost(it) }
        }
    }

    fun destroy() {
        isRendering = false
        renderHandler.removeCallbacksAndMessages(null)
        renderThread.quitSafely()
    }
}
