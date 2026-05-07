package com.pocketpet.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import com.pocketpet.PocketPetApp
import com.pocketpet.R
import com.pocketpet.core.pet.PetEvent
import com.pocketpet.core.pet.PetLoader
import com.pocketpet.core.pet.PetStateMachine
import com.pocketpet.core.personality.PetProfileRepository
import com.pocketpet.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PetOverlayService : Service() {

    @Inject lateinit var petStateMachine: PetStateMachine
    @Inject lateinit var petLoader: PetLoader
    @Inject lateinit var profileRepository: PetProfileRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var petContainer: FrameLayout
    private lateinit var petSurfaceView: PetSurfaceView
    private lateinit var speechBubble: SpeechBubbleView
    private lateinit var params: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var downTime = 0L
    private val longPressThresholdMs = 400L

    companion object {
        const val ACTION_SHOW_BUBBLE = "com.pocketpet.SHOW_BUBBLE"
        const val EXTRA_BUBBLE_TEXT = "bubble_text"
        const val NOTIF_ID = 1001
        const val PET_SIZE_DP = 80
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay()
        startForeground(NOTIF_ID, buildNotification())
        observePetState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_BUBBLE -> {
                val text = intent.getStringExtra(EXTRA_BUBBLE_TEXT) ?: return START_STICKY
                speechBubble.show(text)
            }
        }
        return START_STICKY
    }

    private fun setupOverlay() {
        val density = resources.displayMetrics.density
        val sizePx = (PET_SIZE_DP * density).toInt()

        params = WindowManager.LayoutParams(
            sizePx,
            sizePx + (sizePx * 0.6f).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        petContainer = FrameLayout(this)
        petSurfaceView = PetSurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
        }
        speechBubble = SpeechBubbleView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (sizePx * 2.5f).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (sizePx * 0.1f).toInt()
            }
        }

        petContainer.addView(petSurfaceView)
        petContainer.addView(speechBubble)
        petContainer.setOnTouchListener(::onContainerTouch)

        windowManager.addView(petContainer, params)
        loadPet()
    }

    private fun loadPet() {
        scope.launch(Dispatchers.IO) {
            val profile = profileRepository.profile.first()
            val pet = petLoader.load(profile.petId)
            launch(Dispatchers.Main) {
                petSurfaceView.loadPet(pet)
            }
        }
    }

    private fun observePetState() {
        scope.launch {
            petStateMachine.state.collect { state ->
                petSurfaceView.setState(state.animationKey)
            }
        }
    }

    private fun onContainerTouch(view: View, event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downTime = System.currentTimeMillis()
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                params.x = initialX + (event.rawX - initialTouchX).toInt()
                params.y = initialY + (event.rawY - initialTouchY).toInt()
                windowManager.updateViewLayout(petContainer, params)
                true
            }
            MotionEvent.ACTION_UP -> {
                val elapsed = System.currentTimeMillis() - downTime
                val moved = Math.abs(event.rawX - initialTouchX) > 10 || Math.abs(event.rawY - initialTouchY) > 10
                when {
                    elapsed >= longPressThresholdMs && !moved -> {
                        petStateMachine.send(PetEvent.UserSpeaking)
                        broadcastStartListening()
                    }
                    !moved -> petStateMachine.send(PetEvent.UserTapped)
                }
                true
            }
            else -> false
        }
    }

    private fun broadcastStartListening() {
        sendBroadcast(Intent("com.pocketpet.START_LISTENING"))
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, PocketPetApp.CHANNEL_PET)
            .setSmallIcon(R.drawable.ic_pet_notification)
            .setContentTitle("PocketPet is active")
            .setContentText("Your pet is watching over your notifications")
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        petSurfaceView.destroy()
        if (::petContainer.isInitialized) {
            windowManager.removeView(petContainer)
        }
        super.onDestroy()
    }
}
