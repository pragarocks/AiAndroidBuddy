package com.pocketpet.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
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

    // Volatile so the render thread always sees latest animator
    @Volatile private var animator: SpriteAnimator? = null
    @Volatile private var isRendering = false

    private val destRect = Rect()
    private val frameIntervalMs = 1000L / 30L  // 30 fps

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
        // Pre-clear BOTH double-buffer surfaces so we never see an uninitialized frame
        repeat(2) {
            val c = holder.lockCanvas(null)
            if (c != null) {
                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                holder.unlockCanvasAndPost(c)
            }
        }
        scheduleFrame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        synchronized(destRect) { destRect.set(0, 0, width, height) }
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
        if (!isRendering) return
        val surfaceHolder = holder
        var canvas: Canvas? = null
        try {
            canvas = surfaceHolder.lockCanvas(null) ?: return
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val anim = animator ?: return
            anim.update(System.currentTimeMillis())
            val rect = synchronized(destRect) { Rect(destRect) }
            if (!rect.isEmpty) anim.draw(canvas, rect)
        } finally {
            if (canvas != null) {
                try { surfaceHolder.unlockCanvasAndPost(canvas) }
                catch (_: Exception) {}
            }
        }
    }

    fun destroy() {
        isRendering = false
        renderHandler.removeCallbacksAndMessages(null)
        renderThread.quitSafely()
    }
}
