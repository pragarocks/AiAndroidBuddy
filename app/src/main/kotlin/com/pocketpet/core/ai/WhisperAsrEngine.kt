package com.pocketpet.core.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.nio.FloatBuffer

class WhisperAsrEngine(private val modelPath: String) : AsrEngine {

    private val TAG = "WhisperAsrEngine"
    private val SAMPLE_RATE = 16000
    private val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private val RECORD_SECONDS = 5

    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false

    private fun loadSession() {
        if (session != null) return
        val file = File(modelPath)
        if (!file.exists()) {
            Log.w(TAG, "Whisper model not found at $modelPath")
            return
        }
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                addConfigEntry("session.use_ort_model_bytes_directly", "1")
            }
            session = ortEnv!!.createSession(modelPath, opts)
            Log.i(TAG, "Whisper session loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Whisper: ${e.message}")
        }
    }

    override fun startListening(): Flow<AsrResult> = callbackFlow {
        loadSession()
        isListening = true

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
            .coerceAtLeast(SAMPLE_RATE * 2)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize
        )

        audioRecord = recorder
        recorder.startRecording()

        val totalSamples = SAMPLE_RATE * RECORD_SECONDS
        val pcmBuffer = ShortArray(totalSamples)
        var samplesRead = 0

        while (isListening && samplesRead < totalSamples) {
            val toRead = minOf(bufferSize / 2, totalSamples - samplesRead)
            val read = recorder.read(pcmBuffer, samplesRead, toRead)
            if (read <= 0) break
            samplesRead += read
        }

        recorder.stop()
        recorder.release()
        audioRecord = null

        val floatPcm = pcmBuffer.map { it.toFloat() / 32768f }.toFloatArray()
        val transcript = runInference(floatPcm)
        trySend(AsrResult(transcript, isFinal = true))

        awaitClose { isListening = false }
    }.flowOn(Dispatchers.IO)

    override fun stopListening() {
        isListening = false
        audioRecord?.stop()
    }

    private fun runInference(floatPcm: FloatArray): String {
        val sess = session ?: return "[ASR model not loaded]"
        return try {
            val env = ortEnv ?: return "[no ORT env]"
            val inputTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(floatPcm),
                longArrayOf(1, floatPcm.size.toLong())
            )
            val inputs = mapOf("input_features" to inputTensor)
            val outputs = sess.run(inputs)
            val tokens = (outputs[0].value as LongArray)
            val decoded = tokensToText(tokens)
            inputTensor.close()
            outputs.close()
            decoded
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}")
            "[transcription error]"
        }
    }

    private fun tokensToText(tokens: LongArray): String {
        // In production: use Whisper tokenizer to decode token IDs
        // Simplified placeholder — real implementation uses BPE tokenizer
        return tokens.filter { it > 50257 }.take(50).joinToString(" ") { it.toString() }
    }

    fun close() {
        session?.close()
        ortEnv?.close()
    }
}
