package com.pocketpet.core.pet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PetEvent {
    data object AppStarted : PetEvent
    data object NotificationArrived : PetEvent
    data object UserTapped : PetEvent
    data object UserBeating : PetEvent      // Rapid taps: pet runs away
    data object UserPetting : PetEvent      // Gentle long stroke: pet waves
    data object UserSpeaking : PetEvent     // Long press: start listening
    data object EchoStarted : PetEvent      // ASR listening active
    data object EchoFinished : PetEvent     // Echo TTS done
    data object LlmStarted : PetEvent
    data object LlmGenerating : PetEvent
    data object LlmFinished : PetEvent
    data object LlmError : PetEvent
    data object TtsStarted : PetEvent
    data object TtsFinished : PetEvent
    data object NudgeTriggered : PetEvent   // Health nudge fired
    data object NightModeOn : PetEvent
    data object NightModeOff : PetEvent
    data object BackgroundTaskStarted : PetEvent
    data object BackgroundTaskFinished : PetEvent
    data object ResetToIdle : PetEvent
}

@Singleton
class PetStateMachine @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleReturnJob: Job? = null

    private val _state = MutableStateFlow<PetState>(PetState.Idle)
    val state: StateFlow<PetState> = _state.asStateFlow()

    fun send(event: PetEvent) {
        scope.launch {
            val next = transition(_state.value, event)
            _state.value = next
            // Schedule auto-return to Idle for transient states
            scheduleIdleReturn(next, event)
        }
    }

    private fun scheduleIdleReturn(state: PetState, event: PetEvent) {
        // Cancel any pending return
        idleReturnJob?.cancel()
        val delayMs: Long = when (event) {
            is PetEvent.UserTapped    -> 2_000L   // excited → idle after 2s
            is PetEvent.UserBeating   -> 3_000L   // error/running → idle after 3s
            is PetEvent.UserPetting   -> 2_500L   // excited → idle after 2.5s
            is PetEvent.NudgeTriggered -> 0L      // TTS handles timing
            is PetEvent.EchoFinished  -> 2_500L   // success → idle after 2.5s
            is PetEvent.LlmError      -> 3_000L   // error → idle after 3s
            else -> return                         // No auto-return for others
        }
        if (delayMs > 0) {
            idleReturnJob = scope.launch {
                delay(delayMs)
                _state.value = PetState.Idle
            }
        }
    }

    private fun transition(current: PetState, event: PetEvent): PetState = when (event) {
        is PetEvent.AppStarted           -> PetState.Idle
        is PetEvent.NightModeOn          -> PetState.Sleeping
        is PetEvent.NightModeOff         -> PetState.Idle
        is PetEvent.ResetToIdle          -> PetState.Idle
        is PetEvent.LlmError             -> PetState.Error

        is PetEvent.NotificationArrived  -> when (current) {
            is PetState.Sleeping -> current
            else -> PetState.Excited
        }

        // Tap interactions
        is PetEvent.UserTapped           -> when (current) {
            is PetState.Sleeping -> PetState.Idle
            else -> PetState.Excited
        }
        is PetEvent.UserBeating          -> PetState.Running  // Pet runs away from beating
        is PetEvent.UserPetting          -> PetState.Excited  // Pet waves happily

        // Voice / echo
        is PetEvent.UserSpeaking         -> when (current) {
            is PetState.Sleeping -> current
            else -> PetState.Waiting
        }
        is PetEvent.EchoStarted          -> PetState.Waiting
        is PetEvent.EchoFinished         -> PetState.Success

        // Nudge
        is PetEvent.NudgeTriggered       -> when (current) {
            is PetState.Sleeping -> current
            else -> PetState.Excited
        }

        // LLM (Phase 4 prep)
        is PetEvent.LlmStarted           -> PetState.Working
        is PetEvent.LlmGenerating        -> PetState.Thinking
        is PetEvent.LlmFinished          -> PetState.Success
        is PetEvent.TtsStarted           -> PetState.Success
        is PetEvent.TtsFinished          -> PetState.Idle

        // Background work
        is PetEvent.BackgroundTaskStarted -> when (current) {
            is PetState.Sleeping -> current
            else -> PetState.Running
        }
        is PetEvent.BackgroundTaskFinished -> PetState.Idle
    }
}
