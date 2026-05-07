package com.pocketpet.core.pet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PetEvent {
    data object AppStarted : PetEvent
    data object NotificationArrived : PetEvent
    data object UserSpeaking : PetEvent
    data object LlmStarted : PetEvent
    data object LlmGenerating : PetEvent
    data object LlmFinished : PetEvent
    data object LlmError : PetEvent
    data object TtsStarted : PetEvent
    data object TtsFinished : PetEvent
    data object NightModeOn : PetEvent
    data object NightModeOff : PetEvent
    data object BackgroundTaskStarted : PetEvent
    data object BackgroundTaskFinished : PetEvent
    data object UserTapped : PetEvent
    data object ResetToIdle : PetEvent
}

@Singleton
class PetStateMachine @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<PetState>(PetState.Idle)
    val state: StateFlow<PetState> = _state.asStateFlow()

    fun send(event: PetEvent) {
        scope.launch {
            _state.value = transition(_state.value, event)
        }
    }

    private fun transition(current: PetState, event: PetEvent): PetState = when (event) {
        is PetEvent.AppStarted -> PetState.Idle
        is PetEvent.NightModeOn -> PetState.Sleeping
        is PetEvent.NightModeOff -> PetState.Idle
        is PetEvent.ResetToIdle -> PetState.Idle
        is PetEvent.LlmError -> PetState.Error
        is PetEvent.NotificationArrived -> when (current) {
            is PetState.Sleeping -> current
            else -> PetState.Excited
        }
        is PetEvent.UserSpeaking -> when (current) {
            is PetState.Sleeping -> current
            else -> PetState.Waiting
        }
        is PetEvent.LlmStarted -> PetState.Working
        is PetEvent.LlmGenerating -> PetState.Thinking
        is PetEvent.LlmFinished -> PetState.Success
        is PetEvent.TtsStarted -> PetState.Success
        is PetEvent.TtsFinished -> PetState.Idle
        is PetEvent.BackgroundTaskStarted -> when (current) {
            is PetState.Sleeping -> current
            else -> PetState.Running
        }
        is PetEvent.BackgroundTaskFinished -> PetState.Idle
        is PetEvent.UserTapped -> when (current) {
            is PetState.Sleeping -> PetState.Idle
            else -> current
        }
    }
}
