package com.pocketpet.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pocketpet.PocketPetApp
import com.pocketpet.R
import com.pocketpet.core.pet.PetEvent
import com.pocketpet.core.pet.PetStateMachine
import com.pocketpet.overlay.PetOverlayService
import com.pocketpet.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

private const val TAG = "PetBrain"

/**
 * PetBrainService:
 *  - TTS for tap reactions / nudges (built-in Android TTS, working fine)
 *  - TALKING TOM ECHO: MediaRecorder records mic → MediaPlayer plays back at pitch 1.8×
 *    This completely bypasses TTS for the echo and gives the authentic Talking Tom effect.
 */
@AndroidEntryPoint
class PetBrainService : Service() {

    @Inject lateinit var stateMachine: PetStateMachine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── TTS (for reactions/nudges) ─────────────────────────────────────────
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var audioManager: AudioManager? = null

    // ── Talking Tom: MediaRecorder + MediaPlayer ───────────────────────────
    private var mediaRecorder: MediaRecorder? = null
    private var echoFile: File? = null
    private var isRecording = false

    companion object {
        const val NOTIF_ID = 1002
        // TTS-based speech (reactions, nudges)
        const val ACTION_SPEAK        = "com.pocketpet.SPEAK"
        const val EXTRA_SPEAK_TEXT    = "speak_text"
        const val EXTRA_SPEAK_PITCH   = "speak_pitch"
        const val EXTRA_SPEAK_RATE    = "speak_rate"
        const val ACTION_SHOW_NUDGE   = "com.pocketpet.SHOW_NUDGE"
        const val EXTRA_NUDGE_TEXT    = "nudge_text"
        // Talking Tom recording
        const val ACTION_START_RECORDING  = "com.pocketpet.START_RECORDING"
        const val ACTION_STOP_AND_ECHO    = "com.pocketpet.STOP_AND_ECHO"
        const val ACTION_DISCARD_RECORDING = "com.pocketpet.DISCARD_RECORDING"
    }

    private val ttsBundle by lazy {
        Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_SHOW_NUDGE -> speakNudge(intent.getStringExtra(EXTRA_NUDGE_TEXT) ?: return)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SPEAK -> {
                val text  = intent.getStringExtra(EXTRA_SPEAK_TEXT) ?: return START_STICKY
                val pitch = intent.getFloatExtra(EXTRA_SPEAK_PITCH, 1.8f)
                val rate  = intent.getFloatExtra(EXTRA_SPEAK_RATE, 1.35f)
                ttsSpeak(text, pitch, rate)
            }
            ACTION_START_RECORDING  -> startRecording()
            ACTION_STOP_AND_ECHO    -> stopAndEcho()
            ACTION_DISCARD_RECORDING -> discardRecording()
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        initTts()

        val filter = IntentFilter(ACTION_SHOW_NUDGE)
        val flags = if (Build.VERSION.SDK_INT >= 33) RECEIVER_NOT_EXPORTED else 0
        registerReceiver(receiver, filter, flags)
    }

    // ── TTS ────────────────────────────────────────────────────────────────

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) { Log.e(TAG, "TTS init failed: $status"); return@TextToSpeech }
            val lang = tts?.setLanguage(Locale.getDefault())
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            ttsReady = true
            Log.d(TAG, "TTS ready")
            // Startup greeting
            scope.launch { delay(1200); ttsSpeak(listOf("Hi! I'm here!", "Heyyyy!", "Let's play! 🐾").random()) }
        }
    }

    private fun ttsSpeak(text: String, pitch: Float = 1.8f, rate: Float = 1.35f) {
        val engine = tts
        if (engine == null || !ttsReady) { scope.launch { delay(800); ttsSpeak(text, pitch, rate) }; return }
        requestAudioFocus()
        engine.setPitch(pitch); engine.setSpeechRate(rate)
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, ttsBundle, UUID.randomUUID().toString())
        if (result == TextToSpeech.ERROR) Log.e(TAG, "TTS.speak() ERROR: $text")
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocus() {
        val am = audioManager ?: return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            am.requestAudioFocus(
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs).setAcceptsDelayedFocusGain(false).build()
            )
        } else {
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    // ── Talking Tom: record → pitch-shift playback ─────────────────────────

    private fun startRecording() {
        if (isRecording) return
        val file = File(cacheDir, "echo_${System.currentTimeMillis()}.3gp")
        echoFile = file
        try {
            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(this) else MediaRecorder()
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setAudioSamplingRate(8000)
                setOutputFile(file.absolutePath)
                prepare(); start()
            }
            mediaRecorder = recorder
            isRecording = true
            Log.d(TAG, "Recording started → ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed: ${e.message}")
            isRecording = false
        }
    }

    private fun stopAndEcho() {
        if (!isRecording) { Log.w(TAG, "stopAndEcho: not recording"); return }
        isRecording = false
        try { mediaRecorder?.stop(); mediaRecorder?.release() } catch (e: Exception) { Log.e(TAG, "Stop error: ${e.message}") }
        mediaRecorder = null

        val file = echoFile ?: return
        if (!file.exists() || file.length() < 300) {
            showBubble("I didn't hear anything! 🐾")
            file.delete(); return
        }

        scope.launch(Dispatchers.Main) {
            stateMachine.send(PetEvent.TtsStarted)
            requestAudioFocus()
            try {
                val player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                player.setDataSource(file.absolutePath)
                player.prepare()
                // THE TALKING TOM EFFECT: pitch up + slight speed up
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    player.playbackParams = PlaybackParams().setPitch(1.75f).setSpeed(1.25f)
                }
                player.setOnCompletionListener {
                    it.release(); file.delete()
                    scope.launch { stateMachine.send(PetEvent.EchoFinished) }
                }
                player.start()
                showBubble("Hehe! 😄")
                Log.d(TAG, "Echo playback: pitch=1.75 speed=1.25")
            } catch (e: Exception) {
                Log.e(TAG, "Echo playback failed: ${e.message}")
                file.delete()
                // Fallback to TTS giggle
                ttsSpeak("Hehehehe!", 1.8f, 1.3f)
                stateMachine.send(PetEvent.EchoFinished)
            }
        }
    }

    private fun discardRecording() {
        isRecording = false
        try { mediaRecorder?.stop(); mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
        echoFile?.delete(); echoFile = null
    }

    // ── Nudge ──────────────────────────────────────────────────────────────

    private fun speakNudge(text: String) {
        stateMachine.send(PetEvent.NudgeTriggered)
        showBubble(text)
        ttsSpeak(text, pitch = 1.3f, rate = 1.0f)
        scope.launch { delay(5000); stateMachine.send(PetEvent.TtsFinished) }
    }

    private fun showBubble(text: String) {
        startService(Intent(this, PetOverlayService::class.java).apply {
            action = PetOverlayService.ACTION_SHOW_BUBBLE
            putExtra(PetOverlayService.EXTRA_BUBBLE_TEXT, text)
        })
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, PocketPetApp.CHANNEL_PET)
            .setSmallIcon(R.drawable.ic_pet_notification)
            .setContentTitle("PocketPet active 🐾")
            .setContentText("Hold pet to talk • Tap to play")
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    override fun onDestroy() {
        scope.cancel()
        discardRecording()
        tts?.stop(); tts?.shutdown()
        unregisterReceiver(receiver)
        super.onDestroy()
    }
}
