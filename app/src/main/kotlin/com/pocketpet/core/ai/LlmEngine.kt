package com.pocketpet.core.ai

import kotlinx.coroutines.flow.Flow

interface LlmEngine {
    suspend fun load()
    fun generate(prompt: String, maxTokens: Int = 200): Flow<String>
    fun isLoaded(): Boolean
    fun destroy()
}
