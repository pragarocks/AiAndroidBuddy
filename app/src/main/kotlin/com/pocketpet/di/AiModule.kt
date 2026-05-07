package com.pocketpet.di

import android.content.Context
import com.pocketpet.core.ai.AsrEngine
import com.pocketpet.core.ai.LlmEngine
import com.pocketpet.core.ai.MnnLlmEngine
import com.pocketpet.core.ai.TtsEngine
import com.pocketpet.core.ai.WhisperAsrEngine
import com.pocketpet.core.ai.KokoraTtsEngine
import com.pocketpet.core.ai.ModelConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideLlmEngine(@ApplicationContext context: Context): LlmEngine {
        val modelFile = File(context.filesDir, ModelConfig.LLM_MODEL_FILE)
        return MnnLlmEngine(modelFile.absolutePath)
    }

    @Provides
    @Singleton
    fun provideAsrEngine(@ApplicationContext context: Context): AsrEngine {
        val modelFile = File(context.filesDir, ModelConfig.ASR_MODEL_FILE)
        return WhisperAsrEngine(modelFile.absolutePath)
    }

    @Provides
    @Singleton
    fun provideTtsEngine(@ApplicationContext context: Context): TtsEngine {
        val modelFile = File(context.filesDir, ModelConfig.TTS_MODEL_FILE)
        return KokoraTtsEngine(modelFile.absolutePath)
    }
}
