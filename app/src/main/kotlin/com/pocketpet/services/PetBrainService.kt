package com.pocketpet.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pocketpet.PocketPetApp
import com.pocketpet.R
import com.pocketpet.core.ai.AsrEngine
import com.pocketpet.core.ai.LlmEngine
import com.pocketpet.core.ai.LlmPromptBuilder
import com.pocketpet.core.ai.TtsEngine
import com.pocketpet.core.notifications.NotificationRepository
import com.pocketpet.core.personality.PetProfileRepository
import com.pocketpet.core.pet.PetEvent
import com.pocketpet.core.pet.PetStateMachine
import com.pocketpet.overlay.PetOverlayService
import com.pocketpet.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@AndroidEntryPoint
class PetBrainService : Service() {

    @Inject lateinit var stateMachine: PetStateMachine
    @Inject lateinit var llmEngine: LlmEngine
    @Inject lateinit var asrEngine: AsrEngine
    @Inject lateinit var ttsEngine: TtsEngine
    @Inject lateinit var notificationRepository: NotificationRepository
    @Inject lateinit var profileRepository: PetProfileRepository

    private val promptBuilder = LlmPromptBuilder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val listenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.pocketpet.START_LISTENING") {
                startListeningFlow()
            }
        }
    }

    companion object {
        const val NOTIF_ID = 1002
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        registerReceiver(listenReceiver, IntentFilter("com.pocketpet.START_LISTENING"),
            if (Build.VERSION.SDK_INT >= 33) RECEIVER_NOT_EXPORTED else 0)
        loadLlm()
        observeNewNotifications()
    }

    private fun loadLlm() {
        scope.launch {
            stateMachine.send(PetEvent.BackgroundTaskStarted)
            try {
                withTimeout(60_000) { llmEngine.load() }
            } catch (e: Exception) {
                stateMachine.send(PetEvent.LlmError)
            } finally {
                stateMachine.send(PetEvent.BackgroundTaskFinished)
            }
        }
    }

    private fun observeNewNotifications() {
        scope.launch {
            notificationRepository.newNotification.collect {
                // Auto-summarise after 3 new notifications to avoid spam
            }
        }
    }

    fun summariseNotifications() {
        scope.launch {
            stateMachine.send(PetEvent.LlmStarted)
            try {
                val profile = profileRepository.profile.first()
                val notifications = notificationRepository.getRecentOnce(10)
                if (notifications.isEmpty()) {
                    speak("No notifications to summarise!")
                    return@launch
                }
                val prompt = promptBuilder.buildSummarisePrompt(profile, notifications)
                stateMachine.send(PetEvent.LlmGenerating)
                var result = ""
                withTimeout(30_000) {
                    llmEngine.generate(prompt).collect { result = it }
                }
                stateMachine.send(PetEvent.LlmFinished)
                speak(result)
            } catch (e: Exception) {
                stateMachine.send(PetEvent.LlmError)
                speak("Oops, I had trouble thinking. Try again?")
            }
        }
    }

    private fun startListeningFlow() {
        scope.launch {
            stateMachine.send(PetEvent.UserSpeaking)
            try {
                withTimeout(30_000) {
                    asrEngine.startListening().collect { result ->
                        if (result.isFinal) {
                            processVoiceCommand(result.text)
                        }
                    }
                }
            } catch (e: Exception) {
                stateMachine.send(PetEvent.LlmError)
            }
        }
    }

    private suspend fun processVoiceCommand(text: String) {
        val normalised = text.lowercase()
        when {
            normalised.contains("summarise") || normalised.contains("summarize") ||
            normalised.contains("what's new") || normalised.contains("notifications") -> {
                summariseNotifications()
            }
            normalised.contains("dismiss") -> {
                speak("I'll dismiss that for you.")
                // IntentRecognizer handles action extraction
            }
            normalised.contains("urgent") || normalised.contains("important") -> {
                summariseUrgent()
            }
            else -> {
                speak("I heard \"$text\" but I'm not sure what to do with that yet!")
            }
        }
    }

    private suspend fun summariseUrgent() {
        val profile = profileRepository.profile.first()
        val notifications = notificationRepository.getRecentOnce(20)
            .filter { it.priority >= 1 }
            .take(5)
        if (notifications.isEmpty()) {
            speak("Nothing urgent right now! 🎉")
            return
        }
        val prompt = promptBuilder.buildSummarisePrompt(profile, notifications)
        stateMachine.send(PetEvent.LlmGenerating)
        var result = ""
        withTimeout(30_000) {
            llmEngine.generate(prompt).collect { result = it }
        }
        stateMachine.send(PetEvent.LlmFinished)
        speak(result)
    }

    private fun speak(text: String) {
        scope.launch {
            stateMachine.send(PetEvent.TtsStarted)
            showBubble(text)
            try {
                withTimeout(30_000) { ttsEngine.speak(text) }
            } catch (_: Exception) {}
            stateMachine.send(PetEvent.TtsFinished)
        }
    }

    private fun showBubble(text: String) {
        val intent = Intent(this, PetOverlayService::class.java).apply {
            action = PetOverlayService.ACTION_SHOW_BUBBLE
            putExtra(PetOverlayService.EXTRA_BUBBLE_TEXT, text)
        }
        startService(intent)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, PocketPetApp.CHANNEL_PET)
            .setSmallIcon(R.drawable.ic_pet_notification)
            .setContentTitle("PocketPet brain active")
            .setContentText("AI is ready")
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        unregisterReceiver(listenReceiver)
        llmEngine.destroy()
        ttsEngine.stop()
        super.onDestroy()
    }
}
