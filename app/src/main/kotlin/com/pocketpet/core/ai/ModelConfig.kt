package com.pocketpet.core.ai

object ModelConfig {
    const val LLM_MODEL_FILE = "qwen3.5-0.8b-q4.mnn"
    const val ASR_MODEL_FILE = "whisper-small-int8.onnx"
    const val TTS_MODEL_FILE = "kokoro-int8.onnx"
    const val TTS_VOICES_FILE = "kokoro-voices.bin"

    val ALL_MODELS = listOf(
        ModelInfo(LLM_MODEL_FILE, "Qwen3.5-0.8B LLM", 500L * 1024 * 1024, ModelPriority.HIGH),
        ModelInfo(ASR_MODEL_FILE, "Whisper Small ASR", 150L * 1024 * 1024, ModelPriority.HIGH),
        ModelInfo(TTS_MODEL_FILE, "Kokoro TTS", 80L * 1024 * 1024, ModelPriority.MEDIUM)
    )

    const val TOTAL_MODEL_SIZE_MB = 730L
}

enum class ModelPriority { HIGH, MEDIUM, LOW }

data class ModelInfo(
    val filename: String,
    val displayName: String,
    val sizeBytes: Long,
    val priority: ModelPriority
)

enum class ModelState { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }
