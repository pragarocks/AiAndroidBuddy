package com.pocketpet.core.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stub LLM engine — returns rotating fun responses without loading any model.
 * Phase 4 replaces this with LiteRtLlmEngine (gemma3-1b-it-int4.litertlm via MediaPipe).
 */
class MnnLlmEngine : LlmEngine {

    private var responseIndex = 0

    private val stubResponses = listOf(
        "Hehe, you said that! Say something else! 🐾",
        "Wow, that's interesting! *tilts head curiously*",
        "I don't know what that means but I love it! 😄",
        "That tickles my ears! Tell me more!",
        "Ooh, very wise! You must be a genius! 🌟",
        "*blinks rapidly* Did you just say that? Again, again!",
        "Fascinating! My tiny brain is thinking very hard right now...",
        "I heard every word. Every. Single. One. 👀",
        "That's the best thing anyone has ever said to me!",
        "Hmm... *processes furiously* ...yep, sounds right to me!"
    )

    override suspend fun load() {
        // No-op: no model to load in stub mode
    }

    override fun generate(prompt: String, maxTokens: Int): Flow<String> = flow {
        val response = stubResponses[responseIndex % stubResponses.size]
        responseIndex++
        emit(response)
    }

    override fun isLoaded(): Boolean = true

    override fun destroy() {
        // No-op
    }
}
