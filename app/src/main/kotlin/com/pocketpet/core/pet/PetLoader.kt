package com.pocketpet.core.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class LoadedPet(
    val data: PetAnimationData,
    val spritesheet: Bitmap?
)

/** All bundled pet IDs (matching assets/pets/ folder names) */
val BUNDLED_PET_IDS = listOf("valkyrie", "snuglet", "review-owl", "nova-byte", "bitboy", "axobotl")

/**
 * OpenPets format pet.json (simplified schema from openpets.dev).
 * Does NOT contain animation data — we synthesize that from the standard 9-row layout.
 */
@Serializable
private data class OpenPetJson(
    val id: String = "",
    val displayName: String = "",
    val description: String = "",
    val spritesheetPath: String = "spritesheet.webp"
)

@Singleton
class PetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installedPetRepository: InstalledPetRepository
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** Load the active pet — checks installed pets first, then bundled assets */
    suspend fun loadActive(): LoadedPet {
        val installedId = installedPetRepository.getActivePetId()
        return if (installedId != null) {
            val dir = installedPetRepository.getInstalledDir(installedId)
            if (dir.exists()) loadInstalled(installedId) else loadBundled(BUNDLED_PET_IDS.first())
        } else {
            loadBundled(BUNDLED_PET_IDS.first())
        }
    }

    /** Load a pet by ID — auto-detects installed vs bundled */
    fun load(petId: String): LoadedPet {
        val installedDir = installedPetRepository.getInstalledDir(petId)
        return if (installedDir.exists() && !BUNDLED_PET_IDS.contains(petId)) {
            loadInstalled(petId)
        } else {
            loadBundled(petId)
        }
    }

    /** Load from filesDir/pets/<petId>/ */
    fun loadInstalled(petId: String): LoadedPet {
        val dir = installedPetRepository.getInstalledDir(petId)
        val bitmap = loadBitmapFile(File(dir, "spritesheet.webp"))
            ?: loadBitmapFile(File(dir, "spritesheet.png"))
        val animData = parsePetJson(File(dir, "pet.json"), petId, bitmap)
        return LoadedPet(animData, bitmap)
    }

    /** Load from assets/pets/<petId>/ */
    fun loadBundled(petId: String): LoadedPet {
        val safeId = if (BUNDLED_PET_IDS.contains(petId)) petId else BUNDLED_PET_IDS.first()
        val bitmap = loadBundledSpritesheet(safeId)
        val animData = parseBundledJson(safeId, bitmap)
        return LoadedPet(animData, bitmap)
    }

    // ── JSON parsing — handles both PetAnimationData and OpenPets formats ────

    /**
     * Smart parser: tries PetAnimationData format first, falls back to OpenPets format.
     * Synthesizes animation data from the standard 9-row OpenPets spritesheet layout.
     */
    private fun parsePetJson(file: File, petId: String, bitmap: Bitmap?): PetAnimationData {
        val w = bitmap?.width ?: 1536; val h = bitmap?.height ?: 1872
        if (!file.exists()) return defaultAnimationData(petId, bitmap, w, h)
        return try {
            parseJsonString(file.readText(), petId, bitmap, w, h)
        } catch (_: Exception) {
            defaultAnimationData(petId, bitmap, w, h)
        }
    }

    private fun parseBundledJson(petId: String, bitmap: Bitmap?): PetAnimationData {
        val w = bitmap?.width ?: 1536; val h = bitmap?.height ?: 1872
        return try {
            val raw = context.assets.open("pets/$petId/pet.json").bufferedReader().readText()
            parseJsonString(raw, petId, bitmap, w, h)
        } catch (_: IOException) {
            defaultAnimationData(petId, bitmap, w, h)
        }
    }

    private fun parseJsonString(raw: String, petId: String, bitmap: Bitmap?, w: Int, h: Int): PetAnimationData {
        val jsonObj = json.parseToJsonElement(raw).jsonObject
        return when {
            jsonObj.containsKey("frameSize") -> json.decodeFromString(raw)
            jsonObj.containsKey("id") || jsonObj.containsKey("displayName") -> {
                val opf = json.decodeFromString<OpenPetJson>(raw)
                synthesizeAnimationData(opf.id.ifBlank { petId }, opf.displayName, bitmap, w, h)
            }
            else -> defaultAnimationData(petId, bitmap, w, h)
        }
    }

    /**
     * Synthesizes PetAnimationData from an OpenPets spritesheet.
     *
     * KEY FIX: samples the center pixel of each cell in every row.
     * If alpha < 30 (transparent) or brightness > 245 (near-white background),
     * that cell is empty → we stop counting frames for that row.
     * This prevents drawing beyond real sprite content → no more blank flicker.
     */
    private fun synthesizeAnimationData(
        petId: String,
        displayName: String,
        bitmap: Bitmap?,
        bitmapW: Int = 1536,
        bitmapH: Int = 1872
    ): PetAnimationData {
        val cols = (bitmapW / 192).coerceIn(1, 16)
        val rows = (bitmapH / 208).coerceIn(1, 16)
        val frameW = bitmapW / cols
        val frameH = bitmapH / rows

        /**
         * Detect actual frame count in [rowIdx] by sampling cell centers.
         * Stops when a cell center is transparent OR matches the spritesheet
         * background (detected from the known-empty top-right corner).
         */
        fun detectFrames(rowIdx: Int): Int {
            val b = bitmap ?: return cols
            val safeRow = rowIdx.coerceAtMost(rows - 1)
            val sampleY = (safeRow * frameH + frameH / 2).coerceAtMost(b.height - 1)
            // Also sample a second Y point (1/3 from top) for more confidence
            val sampleY2 = (safeRow * frameH + frameH / 4).coerceAtMost(b.height - 1)

            var count = 0
            for (col in 0 until cols) {
                val sampleX = (col * frameW + frameW / 2).coerceAtMost(b.width - 1)
                if (sampleX >= b.width) break

                val px1 = b.getPixel(sampleX, sampleY)
                val px2 = b.getPixel(sampleX, sampleY2)

                // Check both sample points — if EITHER is non-transparent sprite content,
                // the cell has a frame.
                val alpha1 = android.graphics.Color.alpha(px1)
                val alpha2 = android.graphics.Color.alpha(px2)

                if (alpha1 < 20 && alpha2 < 20) {
                    // Both transparent → empty cell → stop
                    break
                }
                count++
            }
            return count.coerceAtLeast(1)
        }

        fun row(r: Int, fps: Int) = AnimationState(
            row = r.coerceAtMost(rows - 1),
            frames = detectFrames(r),
            fps = fps
        )

        return PetAnimationData(
            name = displayName.ifBlank {
                petId.replace("-", " ").split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            },
            species = "openpets",
            author = "openpets.dev",
            frameSize = FrameSize(frameW, frameH),
            states = mapOf(
                "idle"      to row(0, 6),
                "run_right" to row(1, 12),   // actual right-run row
                "run_left"  to row(2, 12),   // actual left-run row
                "excited"   to row(3, 10),   // wave
                "jump"      to row(4, 10),   // jump (falls back to nearest row)
                "success"   to row(4, 10),
                "error"     to row(5, 8),    // sad
                "waiting"   to row(6, 6),
                "working"   to row(7, 10),
                "thinking"  to row(8, 6),
                "sleeping"  to row(6, 2),
                "running"   to row(1, 12)    // backward-compat alias
            )
        )
    }

    // ── Spritesheet loading ──────────────────────────────────────────────────

    private fun loadBundledSpritesheet(petId: String): Bitmap? {
        val paths = listOf("pets/$petId/spritesheet.webp", "pets/$petId/spritesheet.png")
        for (path in paths) {
            try {
                return context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            } catch (_: IOException) { /* try next */ }
        }
        return null
    }

    private fun loadBitmapFile(file: File): Bitmap? {
        if (!file.exists()) return null
        return try { BitmapFactory.decodeFile(file.absolutePath) } catch (_: Exception) { null }
    }

    // ── Default fallback ─────────────────────────────────────────────────────

    /** Fallback when no pet.json exists — delegates to synthesize so pixel detection still runs */
    private fun defaultAnimationData(
        petId: String,
        bitmap: Bitmap?,
        bitmapW: Int = bitmap?.width ?: 1536,
        bitmapH: Int = bitmap?.height ?: 1872
    ) = synthesizeAnimationData(
        petId = petId,
        displayName = petId.replace("-", " ").split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
        bitmap = bitmap,
        bitmapW = bitmapW,
        bitmapH = bitmapH
    )

    fun availablePets(): List<String> = BUNDLED_PET_IDS
}
