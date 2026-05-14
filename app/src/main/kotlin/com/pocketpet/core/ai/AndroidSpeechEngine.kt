package com.pocketpet.core.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ASR engine backed by Android's built-in SpeechRecognizer — zero model download.
 * Emits partial results while listening, then a final result when done.
 * IMPORTANT: SpeechRecognizer must run on the main thread; flowOn(Dispatchers.Main) ensures this.
 */
@Singleton
class AndroidSpeechEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : AsrEngine {

    override fun startListening(): Flow<AsrResult> = callbackFlow {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                if (partial.isNotBlank()) trySend(AsrResult(partial, isFinal = false))
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
                if (text.isNotBlank()) trySend(AsrResult(text, isFinal = true))
                close()
            }

            override fun onError(error: Int) {
                // Silently close — normal end-of-session scenarios trigger this
                close()
            }
        })

        recognizer.startListening(intent)

        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
        }
    }.flowOn(Dispatchers.Main) // SpeechRecognizer must be on main thread
    
    override fun stopListening() {
        // Flow cancellation via awaitClose handles cleanup
    }
}
