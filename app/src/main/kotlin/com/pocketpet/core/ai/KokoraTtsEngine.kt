package com.pocketpet.core.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.min

class KokoraTtsEngine(private val modelPath: String) : TtsEngine {

    private val TAG = "KokoraTtsEngine"
    private val SAMPLE_RATE = 24000

    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var audioTrack: AudioTrack? = null
    private var isStopped = false

    private fun loadSession() {
        if (session != null) return
        val file = File(modelPath)
        if (!file.exists()) {
            Log.w(TAG, "Kokoro model not found at $modelPath")
            return
        }
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            session = ortEnv!!.createSession(modelPath)
            Log.i(TAG, "Kokoro TTS session loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Kokoro: ${e.message}")
        }
    }

    override suspend fun speak(text: String, speed: Float) = withContext(Dispatchers.Default) {
        loadSession()
        isStopped = false

        val truncated = text.take(200)
        val audioData = runInference(truncated, speed)

        if (audioData == null) {
            Log.w(TAG, "TTS inference returned null — skipping playback")
            return@withContext
        }

        playAudio(audioData)
    }

    private fun runInference(text: String, speed: Float): FloatArray? {
        val sess = session ?: return null
        return try {
            val env = ortEnv ?: return null
            val phonemeIds = textToPhonemeIds(text)
            val phonemeTensor = OnnxTensor.createTensor(
                env,
                java.nio.LongBuffer.wrap(phonemeIds),
                longArrayOf(1, phonemeIds.size.toLong())
            )
            val speedTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(floatArrayOf(speed)),
                longArrayOf(1)
            )
            val inputs = mapOf(
                "phoneme_ids" to phonemeTensor,
                "speed" to speedTensor
            )
            val outputs = sess.run(inputs)
            val audio = outputs[0].value as Array<FloatArray>
            phonemeTensor.close()
            speedTensor.close()
            outputs.close()
            audio[0]
        } catch (e: Exception) {
            Log.e(TAG, "TTS inference error: ${e.message}")
            null
        }
    }

    private fun playAudio(samples: FloatArray) {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(samples.size * 4)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()

        val chunkSize = 4096
        var offset = 0
        while (!isStopped && offset < samples.size) {
            val toWrite = min(chunkSize, samples.size - offset)
            track.write(samples, offset, toWrite, AudioTrack.WRITE_BLOCKING)
            offset += toWrite
        }

        track.stop()
        track.release()
        audioTrack = null
    }

    private fun textToPhonemeIds(text: String): LongArray {
        // Production: use espeak-ng or a Kotlin phonemizer
        // Simplified: map chars to rough token IDs as placeholder
        val padId = 0L
        val bosId = 1L
        val eosId = 2L
        val ids = mutableListOf(bosId)
        text.lowercase().forEach { c ->
            ids.add((c.code % 256).toLong() + 3)
        }
        ids.add(eosId)
        return ids.toLongArray()
    }

    override fun stop() {
        isStopped = true
        audioTrack?.stop()
    }

    fun close() {
        stop()
        session?.close()
        ortEnv?.close()
    }
}
