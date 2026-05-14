package com.pocketpet.overlay

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pocketpet.PocketPetApp
import com.pocketpet.R
import com.pocketpet.core.nudge.NudgeScheduler
import com.pocketpet.core.pet.PetEvent
import com.pocketpet.core.pet.PetLoader
import com.pocketpet.core.pet.PetStateMachine
import com.pocketpet.core.personality.PetProfileRepository
import com.pocketpet.services.PetBrainService
import com.pocketpet.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.random.Random

@AndroidEntryPoint
class PetOverlayService : Service() {

    @Inject lateinit var petStateMachine: PetStateMachine
    @Inject lateinit var petLoader: PetLoader
    @Inject lateinit var profileRepository: PetProfileRepository
    @Inject lateinit var nudgeScheduler: NudgeScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var windowManager: WindowManager
    private lateinit var petContainer: FrameLayout
    private lateinit var bubbleContainer: FrameLayout
    private lateinit var petSurfaceView: PetSurfaceView
    private lateinit var speechBubble: SpeechBubbleView
    private lateinit var petParams: WindowManager.LayoutParams
    private lateinit var bubbleParams: WindowManager.LayoutParams

    private var screenW = 0; private var screenH = 0
    private val petSizeDp = 90; private var petSizePx = 0

    // -- Drag
    private var initialX = 0; private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f
    private var downTime = 0L

    // -- Tap / beat / sad
    private var tapCount = 0
    private val tapWindowMs = 1500L
    private val tapResetRunnable = Runnable { tapCount = 0 }
    private val rapidTapThreshold = 5
    private var consecutiveBeatCount = 0
    private val sadThreshold = 3
    private val beatCountResetRunnable = Runnable { consecutiveBeatCount = 0 }

    // -- Talking Tom toggle mode (long press 600ms in -> loop listen/echo, out -> stop)
    private var talkingTomMode = false
    private var talkingTomJob: Job? = null
    private val recordWindowMs = 3000L
    private val echoBufferMs   = 3000L

    // Long-press detection:
    //   Normal: hold 6s -> enter sleep
    //   Sleeping: hold 3s -> wake up
    private val sleepPressMs     = 6_000L
    private val wakeFromSleepMs  = 3_000L
    private var longPressHandled = false
    private var sleepMode        = false
    private val sleepPressRunnable = Runnable {
        longPressHandled = true
        enterSleepMode()
    }
    private val wakePressRunnable = Runnable {
        longPressHandled = true
        exitSleepMode()
    }

    // Triple-tap for Talking Tom (3 quick taps -> toggle)
    private val tripleTapWindowMs   = 700L
    private val tripleTapRunnable   = Runnable {
        if (tapCount == 3) { tapCount = 0; toggleTalkingTomMode() }
    }

    // -- Movement
    private var movementJob: Job? = null
    private var idleRoamJob: Job? = null
    private val idleTimeoutMs = 20_000L

    companion object {
        const val ACTION_SHOW_BUBBLE = "com.pocketpet.SHOW_BUBBLE"
        const val EXTRA_BUBBLE_TEXT  = "bubble_text"
        const val NOTIF_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val dm = resources.displayMetrics
        screenW = dm.widthPixels; screenH = dm.heightPixels
        petSizePx = (petSizeDp * dm.density).toInt()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupPetOverlay(); setupBubbleOverlay()
        startForeground(NOTIF_ID, buildNotification())
        loadAndObservePet(); scheduleIdleRoam(); nudgeScheduler.scheduleAll()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_BUBBLE) {
            val text = intent.getStringExtra(EXTRA_BUBBLE_TEXT) ?: return START_STICKY
            speechBubble.show(text); syncBubblePosition()
        }
        return START_STICKY
    }

    // -- Overlay windows

    private fun overlayType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun setupPetOverlay() {
        petParams = WindowManager.LayoutParams(
            petSizePx, petSizePx, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = (screenH * 0.35f).toInt() }
        petContainer = FrameLayout(this)
        petSurfaceView = PetSurfaceView(this).apply { layoutParams = FrameLayout.LayoutParams(petSizePx, petSizePx) }
        petContainer.addView(petSurfaceView)
        petContainer.setOnTouchListener(::onPetTouch)
        windowManager.addView(petContainer, petParams)
    }

    private fun setupBubbleOverlay() {
        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 0 }
        bubbleContainer = FrameLayout(this)
        speechBubble = SpeechBubbleView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        bubbleContainer.addView(speechBubble)
        windowManager.addView(bubbleContainer, bubbleParams)
    }

    private fun syncBubblePosition() {
        val doSync = Runnable {
            bubbleContainer.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val bw  = bubbleContainer.measuredWidth.coerceAtLeast(petSizePx)
            val bh  = bubbleContainer.measuredHeight.coerceAtLeast(petSizePx / 3)
            val gap = (8 * resources.displayMetrics.density).toInt()
            bubbleParams.x = (petParams.x + petSizePx / 2 - bw / 2).coerceIn(4, screenW - bw - 4)
            val aboveY = petParams.y - bh - gap
            bubbleParams.y = if (aboveY >= 4) aboveY else petParams.y + petSizePx + gap
            try { windowManager.updateViewLayout(bubbleContainer, bubbleParams) } catch (_: Exception) {}
        }
        mainHandler.post(doSync)
        mainHandler.postDelayed(doSync, 80)
    }

    // -- Pet loading

    private fun loadAndObservePet() {
        scope.launch {
            var lastId = ""
            profileRepository.profile.collect { profile ->
                if (profile.petId != lastId) {
                    lastId = profile.petId
                    val pet = withContext(Dispatchers.IO) { petLoader.load(profile.petId) }
                    petSurfaceView.loadPet(pet)
                }
            }
        }
        scope.launch {
            petStateMachine.state.collect { state ->
                petSurfaceView.setState(state.animationKey)
            }
        }
    }

    // -- Talking Tom toggle mode

    private fun toggleTalkingTomMode() {
        if (talkingTomMode) {
            talkingTomMode = false
            talkingTomJob?.cancel()
            brainCommand(PetBrainService.ACTION_DISCARD_RECORDING)
            speechBubble.show("Bye bye! Stopping echo mode.")
            petStateMachine.send(PetEvent.ResetToIdle)
        } else {
            val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            if (!hasAudio) { speechBubble.show("Need mic permission to talk!"); return }
            talkingTomMode = true
            speechBubble.show("Talking Tom ON! Long press to stop.")
            talkingTomJob = scope.launch { delay(800); talkingTomLoop() }
        }
    }

    /** Auto-loop: record 3s -> echo -> record again until mode exits */
    private suspend fun talkingTomLoop() {
        while (talkingTomMode) {
            // LISTEN phase: waiting/ears-up animation
            petStateMachine.send(PetEvent.EchoStarted)
            petSurfaceView.setState("waiting")
            speechBubble.show("Listening...")
            brainCommand(PetBrainService.ACTION_START_RECORDING)
            delay(recordWindowMs)
            if (!talkingTomMode) break

            // ECHO phase: jump/bounce animation while playing back
            brainCommand(PetBrainService.ACTION_STOP_AND_ECHO)
            speechBubble.show("Hehe!")
            // Animate pet: alternate jump and idle during echo playback
            val echoEnd = System.currentTimeMillis() + echoBufferMs
            var jumpToggle = true
            while (System.currentTimeMillis() < echoEnd && talkingTomMode) {
                petSurfaceView.setState(if (jumpToggle) "jump" else "excited")
                jumpToggle = !jumpToggle
                delay(400)
            }
            if (!talkingTomMode) break
            petStateMachine.send(PetEvent.EchoFinished)
            petSurfaceView.setState("idle")
            delay(400)
        }
    }

    // -- Idle roaming: walk continuously, bounce at walls

    private fun scheduleIdleRoam() {
        idleRoamJob?.cancel()
        idleRoamJob = scope.launch { delay(idleTimeoutMs); roamLoop() }
    }

    /**
     * Random behavior state machine — feels organic, never repetitive.
     *
     * Behaviors (weighted):
     *   WALK       40% — run in one direction, N steps, stop or continue
     *   IDLE_REST  20% — stand in idle for 1-3s (thinking/waiting)
     *   THINKING   12% — show thinking animation, then look confused
     *   EXCITED    10% — do an excited/wave burst in place
     *   TELEPORT    8% — jump → blink to random location
     *   STRETCH     6% — waiting anim + idle, then yawn
     *   SPIN        4% — quick left/right direction flick
     */
    private suspend fun roamLoop() {
        val stepPx = (8 * resources.displayMetrics.density).toInt()   // was 10 — slightly slower
        var goRight = petParams.x < screenW / 2

        while (true) {
            val roll = Random.nextInt(100)
            when {
                // ── WALK (40%) ──────────────────────────────────────────────
                roll < 40 -> {
                    val steps = Random.nextInt(6, 22)
                    repeat(steps) {
                        petSurfaceView.setState(if (goRight) "run_right" else "run_left")
                        delay(190)   // was 145ms — gentle stroll speed
                        val newX = if (goRight) petParams.x + stepPx else petParams.x - stepPx
                        val hitWall = (goRight && newX >= screenW - petSizePx) || (!goRight && newX <= 0)
                        petParams.x = newX.coerceIn(0, screenW - petSizePx)
                        updatePetPosition(); syncBubblePosition()
                        if (hitWall) {
                            petSurfaceView.setState("excited"); delay(280)
                            goRight = !goRight
                            return@repeat
                        }
                    }
                }

                // ── IDLE REST (20%) ─────────────────────────────────────────
                roll < 60 -> {
                    petSurfaceView.setState("idle")
                    delay(Random.nextLong(1200L, 3500L))
                }

                // ── THINKING (12%) ──────────────────────────────────────────
                roll < 72 -> {
                    petSurfaceView.setState("thinking")
                    delay(Random.nextLong(1500L, 2800L))
                    // Briefly look left then right (direction flick)
                    petSurfaceView.setState("run_left"); delay(120)
                    petSurfaceView.setState("run_right"); delay(120)
                    petSurfaceView.setState("idle"); delay(400)
                }

                // ── EXCITED BURST (10%) ─────────────────────────────────────
                roll < 82 -> {
                    repeat(3) {
                        petSurfaceView.setState("excited"); delay(250)
                        petSurfaceView.setState("jump");    delay(250)
                    }
                    petSurfaceView.setState("idle"); delay(300)
                }

                // ── TELEPORT HOP (8%) ───────────────────────────────────────
                roll < 90 -> {
                    petSurfaceView.setState("jump"); delay(240)
                    petSurfaceView.visibility = View.INVISIBLE
                    petParams.x = Random.nextInt(petSizePx, screenW - petSizePx)
                    petParams.y = Random.nextInt((screenH * 0.1f).toInt(), (screenH * 0.82f).toInt())
                    updatePetPosition(); syncBubblePosition()
                    delay(180)
                    petSurfaceView.visibility = View.VISIBLE
                    goRight = Random.nextBoolean()
                    petSurfaceView.setState("idle"); delay(500)
                }

                // ── STRETCH / WAIT (6%) ─────────────────────────────────────
                roll < 96 -> {
                    petSurfaceView.setState("waiting")
                    delay(Random.nextLong(1000L, 2000L))
                    petSurfaceView.setState("idle"); delay(600)
                }

                // ── SPIN (4%) ───────────────────────────────────────────────
                else -> {
                    // Quick direction flick — looks like it's spinning
                    repeat(4) {
                        petSurfaceView.setState("run_right"); delay(100)
                        petSurfaceView.setState("run_left");  delay(100)
                    }
                    goRight = Random.nextBoolean()
                    petSurfaceView.setState("idle"); delay(300)
                }
            }

            // Brief mandatory gap between behaviors (feels natural)
            delay(Random.nextLong(150L, 600L))
        }
    }

    // -- Beat -> run away: direction is re-asserted every frame so state machine can't override it

    private fun animateRunAway() {
        movementJob?.cancel()
        movementJob = scope.launch {
            val goRight  = petParams.x < screenW / 2
            val runAnim  = if (goRight) "run_right" else "run_left"
            val stepPx   = (18 * resources.displayMetrics.density).toInt()  // was 24 — slightly slower
            petStateMachine.send(PetEvent.UserBeating)
            var hitBorder = false
            while (!hitBorder) {
                // Re-assert direction every frame — prevents state machine observer from overriding
                petSurfaceView.setState(runAnim)
                delay(70)   // was 45ms — slightly slower, smoother dash
                petParams.x = if (goRight) (petParams.x + stepPx).coerceAtMost(screenW - petSizePx)
                              else (petParams.x - stepPx).coerceAtLeast(0)
                updatePetPosition(); syncBubblePosition()
                hitBorder = (goRight && petParams.x >= screenW - petSizePx) || (!goRight && petParams.x <= 0)
            }
            petSurfaceView.setState("jump"); delay(350)
            petSurfaceView.visibility = View.INVISIBLE; delay(550)
            val margin = (petSizePx * 2f).toInt()
            petParams.x = if (goRight) Random.nextInt(4, margin)
                          else Random.nextInt(screenW - margin, screenW - petSizePx - 4)
            petParams.y = Random.nextInt((screenH * 0.15f).toInt(), (screenH * 0.75f).toInt())
            updatePetPosition(); syncBubblePosition()
            petSurfaceView.visibility = View.VISIBLE
            petSurfaceView.alpha = 0f
            petSurfaceView.animate().alpha(1f).setDuration(300).start()
            petSurfaceView.setState("idle")
            petStateMachine.send(PetEvent.ResetToIdle)
        }
    }

    // -- Touch: tap (rapid=beat, 3-tap=Talking Tom), 6s long press = sleep, drag

    private fun onPetTouch(view: View, event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downTime = System.currentTimeMillis()
                longPressHandled = false
                initialX = petParams.x; initialY = petParams.y
                initialTouchX = event.rawX; initialTouchY = event.rawY
                // Sleeping: 3s to wake. Awake: 6s to sleep.
                if (sleepMode) mainHandler.postDelayed(wakePressRunnable, wakeFromSleepMs)
                else           mainHandler.postDelayed(sleepPressRunnable, sleepPressMs)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX; val dy = event.rawY - initialTouchY
                if (abs(dx) > 12 || abs(dy) > 12) {
                    mainHandler.removeCallbacks(sleepPressRunnable)
                    mainHandler.removeCallbacks(wakePressRunnable)
                    petParams.x = (initialX + dx.toInt()).coerceIn(0, screenW - petSizePx)
                    petParams.y = (initialY + dy.toInt()).coerceIn(0, screenH - petSizePx)
                    updatePetPosition(); syncBubblePosition()
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(sleepPressRunnable)
                mainHandler.removeCallbacks(wakePressRunnable)
                val moved = abs(event.rawX - initialTouchX) > 12 || abs(event.rawY - initialTouchY) > 12
                when {
                    longPressHandled -> resetIdleTimer()
                    moved            -> resetIdleTimer()
                    else             -> if (!sleepMode) onTap()  // ignore taps while sleeping
                }
                true
            }
            else -> false
        }
    }

    private fun onTap() {
        resetIdleTimer()
        // Cancel any pending triple-tap detection before re-evaluating
        mainHandler.removeCallbacks(tripleTapRunnable)
        mainHandler.removeCallbacks(tapResetRunnable)
        tapCount++

        when {
            // 5+ rapid taps = beat/run
            tapCount >= rapidTapThreshold -> {
                tapCount = 0
                consecutiveBeatCount++
                mainHandler.removeCallbacks(beatCountResetRunnable)
                if (consecutiveBeatCount >= sadThreshold) {
                    consecutiveBeatCount = 0
                    val msg = sadReactions.random()
                    speechBubble.show(msg); syncBubblePosition(); speak(msg, 1.2f, 0.9f)
                    petStateMachine.send(PetEvent.LlmError)
                } else {
                    mainHandler.postDelayed(beatCountResetRunnable, 25_000L)
                    val msg = beatReactions.random()
                    speechBubble.show(msg); syncBubblePosition(); speak(msg)
                    animateRunAway()
                }
            }
            // Exactly 3 taps -> wait 350ms; if no 4th tap arrives, toggle Talking Tom
            tapCount == 3 -> {
                mainHandler.postDelayed(tripleTapRunnable, 350L)
                mainHandler.postDelayed(tapResetRunnable, tapWindowMs)
            }
            // 1st tap -> react
            tapCount == 1 -> {
                val msg = tapReactions.random()
                speechBubble.show(msg); syncBubblePosition(); speak(msg)
                petStateMachine.send(PetEvent.UserTapped)
                mainHandler.postDelayed(tapResetRunnable, tapWindowMs)
            }
            else -> mainHandler.postDelayed(tapResetRunnable, tapWindowMs)
        }
    }

    // -- Sleep mode: hold 6s -> fly to top-right corner, shrink 40%, sleep. Hold 6s again -> come alive.

    private fun enterSleepMode() {
        if (sleepMode) return
        sleepMode = true
        idleRoamJob?.cancel(); movementJob?.cancel()
        talkingTomMode = false; talkingTomJob?.cancel()
        speechBubble.show("Zzz... hold 6s to wake me"); syncBubblePosition()

        scope.launch {
            // Fly to top-right corner with run animation
            val targetX = screenW - petSizePx - 8
            val targetY = 8
            petSurfaceView.setState("run_right")
            val steps = 20
            val dx = (targetX - petParams.x) / steps
            val dy = (targetY - petParams.y) / steps
            repeat(steps) {
                petParams.x = (petParams.x + dx).coerceIn(0, screenW - petSizePx)
                petParams.y = (petParams.y + dy).coerceIn(0, screenH - petSizePx)
                updatePetPosition(); syncBubblePosition()
                delay(30)
            }
            petParams.x = targetX; petParams.y = targetY
            updatePetPosition(); syncBubblePosition()
            // Shrink to 40% and show sleep animation — tiny, unobtrusive
            petSurfaceView.animate().scaleX(0.4f).scaleY(0.4f).setDuration(400).start()
            delay(400)
            petSurfaceView.setState("sleeping")
            speechBubble.hide()
        }
    }

    private fun exitSleepMode() {
        if (!sleepMode) return
        sleepMode = false

        scope.launch {
            // Wake up: restore size, show excited, fly back to center
            petSurfaceView.animate().scaleX(1f).scaleY(1f).setDuration(400).start()
            delay(200)
            petSurfaceView.setState("excited")
            speechBubble.show("I'm awake! Let's play!")
            syncBubblePosition()
            speak("I am awake! Let us play!")
            delay(400)

            val targetX = screenW / 2 - petSizePx / 2
            val targetY = (screenH * 0.4f).toInt()
            petSurfaceView.setState("run_left")
            val steps = 25
            val dx = (targetX - petParams.x) / steps
            val dy = (targetY - petParams.y) / steps
            repeat(steps) {
                petParams.x = (petParams.x + dx).coerceIn(0, screenW - petSizePx)
                petParams.y = (petParams.y + dy).coerceIn(0, screenH - petSizePx)
                updatePetPosition(); syncBubblePosition()
                delay(28)
            }
            petParams.x = targetX; petParams.y = targetY
            updatePetPosition(); syncBubblePosition()
            petSurfaceView.setState("idle")
            scheduleIdleRoam()
        }
    }

    private fun resetIdleTimer() {
        idleRoamJob?.cancel()
        if (movementJob?.isActive == true) { movementJob?.cancel(); petStateMachine.send(PetEvent.ResetToIdle) }
        scheduleIdleRoam()
    }

    // -- Helpers

    private fun updatePetPosition() { try { windowManager.updateViewLayout(petContainer, petParams) } catch (_: Exception) {} }

    private fun brainCommand(action: String) {
        startService(Intent(this, PetBrainService::class.java).apply { this.action = action })
    }

    private fun speak(text: String, pitch: Float = 1.8f, rate: Float = 1.35f) {
        startService(Intent(this, PetBrainService::class.java).apply {
            action = PetBrainService.ACTION_SPEAK
            putExtra(PetBrainService.EXTRA_SPEAK_TEXT, text)
            putExtra(PetBrainService.EXTRA_SPEAK_PITCH, pitch)
            putExtra(PetBrainService.EXTRA_SPEAK_RATE, rate)
        })
    }

    private val tapReactions  = listOf("Hehe! That tickles!", "Hey!", "Weeee!", "*happy noises*")
    private val beatReactions = listOf("Ow ow! Stop!", "I'm outta here!", "NOT COOL!", "Help!")
    private val sadReactions  = listOf("Please... no more", "That hurts", "I just wanted a friend")

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, PocketPetApp.CHANNEL_PET)
            .setSmallIcon(R.drawable.ic_pet_notification)
            .setContentTitle("PocketPet active")
            .setContentText("Long press to talk | Tap to play")
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    override fun onDestroy() {
        scope.cancel(); petSurfaceView.destroy()
        try { windowManager.removeView(petContainer) } catch (_: Exception) {}
        try { windowManager.removeView(bubbleContainer) } catch (_: Exception) {}
        nudgeScheduler.cancel(); super.onDestroy()
    }
}
