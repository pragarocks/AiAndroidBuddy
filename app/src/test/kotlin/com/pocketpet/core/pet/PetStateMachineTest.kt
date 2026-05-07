package com.pocketpet.core.pet

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PetStateMachineTest {

    private lateinit var sm: PetStateMachine

    @Before
    fun setUp() {
        sm = PetStateMachine()
    }

    private suspend fun sendAndWait(event: PetEvent) {
        sm.send(event)
        kotlinx.coroutines.delay(100)
    }

    @Test
    fun `starts in idle state`() {
        assertEquals(PetState.Idle, sm.state.value)
    }

    @Test
    fun `notification arrives transitions to excited`() = runTest {
        sendAndWait(PetEvent.NotificationArrived)
        assertEquals(PetState.Excited, sm.state.value)
    }

    @Test
    fun `notification does not wake sleeping pet`() = runTest {
        sendAndWait(PetEvent.NightModeOn)
        assertEquals(PetState.Sleeping, sm.state.value)
        sendAndWait(PetEvent.NotificationArrived)
        assertEquals(PetState.Sleeping, sm.state.value)
    }

    @Test
    fun `llm pipeline states progress correctly`() = runTest {
        sendAndWait(PetEvent.LlmStarted)
        assertEquals(PetState.Working, sm.state.value)

        sendAndWait(PetEvent.LlmGenerating)
        assertEquals(PetState.Thinking, sm.state.value)

        sendAndWait(PetEvent.LlmFinished)
        assertEquals(PetState.Success, sm.state.value)

        sendAndWait(PetEvent.TtsFinished)
        assertEquals(PetState.Idle, sm.state.value)
    }

    @Test
    fun `llm error transitions to error state`() = runTest {
        sendAndWait(PetEvent.LlmStarted)
        sendAndWait(PetEvent.LlmError)
        assertEquals(PetState.Error, sm.state.value)
    }

    @Test
    fun `night mode on puts pet to sleep`() = runTest {
        sendAndWait(PetEvent.NightModeOn)
        assertEquals(PetState.Sleeping, sm.state.value)
    }

    @Test
    fun `night mode off wakes pet to idle`() = runTest {
        sendAndWait(PetEvent.NightModeOn)
        sendAndWait(PetEvent.NightModeOff)
        assertEquals(PetState.Idle, sm.state.value)
    }

    @Test
    fun `reset to idle works from error state`() = runTest {
        sendAndWait(PetEvent.LlmError)
        assertEquals(PetState.Error, sm.state.value)
        sendAndWait(PetEvent.ResetToIdle)
        assertEquals(PetState.Idle, sm.state.value)
    }

    @Test
    fun `user tapping sleeping pet wakes to idle`() = runTest {
        sendAndWait(PetEvent.NightModeOn)
        sendAndWait(PetEvent.UserTapped)
        assertEquals(PetState.Idle, sm.state.value)
    }

    @Test
    fun `background task running and finished`() = runTest {
        sendAndWait(PetEvent.BackgroundTaskStarted)
        assertEquals(PetState.Running, sm.state.value)
        sendAndWait(PetEvent.BackgroundTaskFinished)
        assertEquals(PetState.Idle, sm.state.value)
    }

    @Test
    fun `animation key matches state`() {
        assertEquals("idle", PetState.Idle.animationKey)
        assertEquals("excited", PetState.Excited.animationKey)
        assertEquals("working", PetState.Working.animationKey)
        assertEquals("thinking", PetState.Thinking.animationKey)
        assertEquals("success", PetState.Success.animationKey)
        assertEquals("error", PetState.Error.animationKey)
        assertEquals("waiting", PetState.Waiting.animationKey)
        assertEquals("sleeping", PetState.Sleeping.animationKey)
        assertEquals("running", PetState.Running.animationKey)
    }
}
