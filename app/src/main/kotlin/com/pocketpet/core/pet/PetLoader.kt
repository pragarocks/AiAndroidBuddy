package com.pocketpet.core.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class LoadedPet(
    val data: PetAnimationData,
    val spritesheet: Bitmap?
)

@Singleton
class PetLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(petId: String): LoadedPet {
        val animData = loadAnimationData(petId)
        val bitmap = loadSpritesheet(petId)
        return LoadedPet(animData, bitmap)
    }

    private fun loadAnimationData(petId: String): PetAnimationData {
        return try {
            val raw = context.assets.open("pets/$petId/pet.json").bufferedReader().readText()
            json.decodeFromString(raw)
        } catch (e: IOException) {
            defaultAnimationData(petId)
        }
    }

    private fun loadSpritesheet(petId: String): Bitmap? {
        return try {
            context.assets.open("pets/$petId/spritesheet.webp").use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun defaultAnimationData(petId: String) = PetAnimationData(
        name = petId.replaceFirstChar { it.uppercase() },
        species = "blob",
        frameSize = FrameSize(192, 208),
        states = mapOf(
            "idle" to AnimationState(0, 4, 4),
            "thinking" to AnimationState(1, 6, 8),
            "working" to AnimationState(2, 6, 8),
            "sleeping" to AnimationState(3, 4, 2),
            "success" to AnimationState(4, 5, 10),
            "error" to AnimationState(5, 4, 6),
            "waiting" to AnimationState(6, 4, 5),
            "excited" to AnimationState(7, 6, 12),
            "running" to AnimationState(8, 6, 10)
        )
    )

    fun availablePets(): List<String> {
        return try {
            context.assets.list("pets")?.toList() ?: listOf("boba")
        } catch (e: IOException) {
            listOf("boba")
        }
    }
}
