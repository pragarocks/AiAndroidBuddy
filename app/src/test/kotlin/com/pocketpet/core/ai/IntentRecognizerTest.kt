package com.pocketpet.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IntentRecognizerTest {

    private lateinit var recognizer: IntentRecognizer

    @Before
    fun setUp() {
        recognizer = IntentRecognizer()
    }

    @Test
    fun `recognises summarise intent`() {
        val intent = recognizer.recognise("summarise my notifications")
        assertEquals(IntentRecognizer.Intent.Summarise, intent)
    }

    @Test
    fun `recognises summarize with z spelling`() {
        val intent = recognizer.recognise("summarize everything")
        assertEquals(IntentRecognizer.Intent.Summarise, intent)
    }

    @Test
    fun `recognises what is new`() {
        val intent = recognizer.recognise("what's new")
        assertEquals(IntentRecognizer.Intent.Summarise, intent)
    }

    @Test
    fun `recognises urgent intent`() {
        val intent = recognizer.recognise("what's urgent")
        assertEquals(IntentRecognizer.Intent.Urgent, intent)
    }

    @Test
    fun `recognises dismiss intent`() {
        val intent = recognizer.recognise("dismiss the WhatsApp message")
        assertTrue(intent is IntentRecognizer.Intent.DismissNotification)
    }

    @Test
    fun `recognises reply intent`() {
        val intent = recognizer.recognise("reply to Alice saying yes I'm coming")
        assertTrue(intent is IntentRecognizer.Intent.ReplyNotification)
    }

    @Test
    fun `recognises open intent`() {
        val intent = recognizer.recognise("open the Gmail notification")
        assertTrue(intent is IntentRecognizer.Intent.OpenNotification)
    }

    @Test
    fun `unknown command returns Unknown`() {
        val intent = recognizer.recognise("play some music")
        assertEquals(IntentRecognizer.Intent.Unknown, intent)
    }
}
