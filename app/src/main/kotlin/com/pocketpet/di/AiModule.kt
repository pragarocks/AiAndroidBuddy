package com.pocketpet.di

import android.content.Context
import com.pocketpet.core.ai.AndroidSpeechEngine
import com.pocketpet.core.ai.AndroidTtsEngine
import com.pocketpet.core.ai.AsrEngine
import com.pocketpet.core.ai.LlmEngine
import com.pocketpet.core.ai.MnnLlmEngine
import com.pocketpet.core.ai.TtsEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    /** Phase 4 swap: replace MnnLlmEngine with LiteRtLlmEngine (gemma3-1b-it-int4.litertlm) */
    @Provides
    @Singleton
    fun provideLlmEngine(): LlmEngine = MnnLlmEngine()

    /** Built-in Android SpeechRecognizer — zero model download, offline-preferred */
    @Provides
    @Singleton
    fun provideAsrEngine(@ApplicationContext context: Context): AsrEngine =
        AndroidSpeechEngine(context)

    /** Talking Tom-style TTS — high pitch + fast rate, zero model download */
    @Provides
    @Singleton
    fun provideTtsEngine(@ApplicationContext context: Context): TtsEngine =
        AndroidTtsEngine(context)
}
