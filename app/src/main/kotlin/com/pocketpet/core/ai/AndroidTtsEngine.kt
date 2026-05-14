package com.pocketpet.core.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Talking Tom-style TTS: high pitch (1.75f) + fast rate (1.35f) = funny animal voice.
 * Uses Android's built-in TextToSpeech — zero model download required.
 */
@Singleton
class AndroidTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TtsEngine {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isReady = true
            }
        }
    }

    override suspend fun speak(text: String, speed: Float) {
        val engine = tts ?: return
        if (!isReady) return

        // Talking Tom feel: high pitch + slightly fast = funny animal voice
        engine.setSpeechRate(1.35f)
        engine.setPitch(1.75f)

        val utteranceId = UUID.randomUUID().toString()
        suspendCancellableCoroutine { cont ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(uid: String?) {}
                override fun onDone(uid: String?) { if (cont.isActive) cont.resume(Unit) }
                @Deprecated("Deprecated in Java")
                override fun onError(uid: String?) { if (cont.isActive) cont.resume(Unit) }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (cont.isActive) cont.resume(Unit)
                }
            })
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            cont.invokeOnCancellation { engine.stop() }
        }
    }

    override fun stop() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
