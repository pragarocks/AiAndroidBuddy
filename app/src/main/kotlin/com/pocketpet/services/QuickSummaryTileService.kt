package com.pocketpet.services

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.pocketpet.core.ai.LlmEngine
import com.pocketpet.core.ai.LlmPromptBuilder
import com.pocketpet.core.notifications.NotificationRepository
import com.pocketpet.core.personality.PetProfileRepository
import com.pocketpet.core.pet.PetEvent
import com.pocketpet.core.pet.PetStateMachine
import com.pocketpet.overlay.PetOverlayService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class QuickSummaryTileService : TileService() {

    @Inject lateinit var llmEngine: LlmEngine
    @Inject lateinit var notificationRepository: NotificationRepository
    @Inject lateinit var profileRepository: PetProfileRepository
    @Inject lateinit var stateMachine: PetStateMachine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val promptBuilder = LlmPromptBuilder()

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (qsTile?.state == Tile.STATE_UNAVAILABLE) return

        setTileState(Tile.STATE_ACTIVE, "Summarising...")
        scope.launch { summarise() }
    }

    private suspend fun summarise() {
        stateMachine.send(PetEvent.LlmStarted)
        try {
            val profile = profileRepository.profile.first()
            val notifications = notificationRepository.getRecentOnce(10)

            if (notifications.isEmpty()) {
                showBubble("No recent notifications! You're all caught up 🎉")
                setTileState(Tile.STATE_INACTIVE, "No notifications")
                stateMachine.send(PetEvent.ResetToIdle)
                return
            }

            stateMachine.send(PetEvent.LlmGenerating)
            val prompt = promptBuilder.buildSummarisePrompt(profile, notifications)

            var result = ""
            withTimeout(30_000) {
                llmEngine.generate(prompt).collect { result = it }
            }

            stateMachine.send(PetEvent.LlmFinished)
            showBubble(result)
            setTileState(Tile.STATE_INACTIVE, "Done")
        } catch (e: Exception) {
            stateMachine.send(PetEvent.LlmError)
            showBubble("Couldn't summarise right now. Try again?")
            setTileState(Tile.STATE_INACTIVE, "PocketPet")
        }
    }

    private fun showBubble(text: String) {
        val intent = Intent(this, PetOverlayService::class.java).apply {
            action = PetOverlayService.ACTION_SHOW_BUBBLE
            putExtra(PetOverlayService.EXTRA_BUBBLE_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startService(intent)
    }

    private fun updateTileState() {
        val ready = llmEngine.isLoaded()
        setTileState(
            if (ready) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE,
            if (ready) "PocketPet" else "Loading AI..."
        )
    }

    private fun setTileState(state: Int, label: String) {
        qsTile?.let { tile ->
            tile.state = state
            tile.label = label
            tile.updateTile()
        }
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
