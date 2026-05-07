package com.pocketpet.core.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class MnnLlmEngine(private val modelPath: String) : LlmEngine {

    private var handle: Long = 0

    companion object {
        private const val TAG = "MnnLlmEngine"

        init {
            try {
                System.loadLibrary("mnn_bridge")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "mnn_bridge native library not found — running in stub mode")
            }
        }
    }

    external fun nativeInit(modelPath: String): Long
    external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int): String
    external fun nativeDestroy(handle: Long)

    override suspend fun load() {
        if (!File(modelPath).exists()) {
            Log.w(TAG, "Model file not found at $modelPath — running in stub mode")
            return
        }
        handle = try {
            nativeInit(modelPath)
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native not available: ${e.message}")
            0L
        }
        Log.i(TAG, if (handle != 0L) "LLM loaded" else "LLM load failed or stub")
    }

    override fun generate(prompt: String, maxTokens: Int): Flow<String> = flow {
        val result = try {
            if (handle != 0L) {
                nativeGenerate(handle, prompt, maxTokens)
            } else {
                stubResponse(prompt)
            }
        } catch (e: UnsatisfiedLinkError) {
            stubResponse(prompt)
        }
        emit(result)
    }.flowOn(Dispatchers.Default)

    override fun isLoaded(): Boolean = handle != 0L

    override fun destroy() {
        if (handle != 0L) {
            try { nativeDestroy(handle) } catch (_: UnsatisfiedLinkError) {}
            handle = 0L
        }
    }

    private fun stubResponse(prompt: String): String {
        return when {
            prompt.contains("summarise", ignoreCase = true) || prompt.contains("summarize", ignoreCase = true) ->
                "You have a few notifications. I'll check them when the AI model is ready!"
            prompt.contains("urgent", ignoreCase = true) ->
                "I'll flag urgent ones once my brain is fully loaded!"
            else -> "Hmm, I'm still loading my thoughts. Give me a moment!"
        }
    }
}
