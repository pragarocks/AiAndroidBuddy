package com.pocketpet.core.ai

interface TtsEngine {
    suspend fun speak(text: String, speed: Float = 1.1f)
    fun stop()
}
