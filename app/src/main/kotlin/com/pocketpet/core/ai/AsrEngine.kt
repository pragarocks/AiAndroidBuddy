package com.pocketpet.core.ai

import kotlinx.coroutines.flow.Flow

data class AsrResult(val text: String, val isFinal: Boolean)

interface AsrEngine {
    fun startListening(): Flow<AsrResult>
    fun stopListening()
}
