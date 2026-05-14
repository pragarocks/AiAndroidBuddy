package com.pocketpet.core.pet

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_ZIP_BYTES = 30 * 1024 * 1024L  // 30 MB limit
private const val MAX_EXTRACT_BYTES = 60 * 1024 * 1024L // 60 MB extracted limit
private const val MAX_ENTRIES = 50

/**
 * Installs a pet from a local .zip file (via SAF URI).
 * Security: path-traversal check, size limits, validates pet.json + spritesheet presence.
 */
@Singleton
class PetInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installedPetRepository: InstalledPetRepository
) {
    /**
     * Install a pet from a content:// URI (Storage Access Framework).
     * Returns the installed petId on success.
     */
    suspend fun installFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(uri)
                ?: error("Cannot open zip stream")

            val entries = mutableListOf<Pair<String, ByteArray>>()
            var totalBytes = 0L

            ZipInputStream(stream.buffered()).use { zip ->
                var entry = zip.nextEntry
                var entryCount = 0

                while (entry != null) {
                    entryCount++
                    if (entryCount > MAX_ENTRIES) error("Zip has too many entries (max $MAX_ENTRIES)")

                    val name = entry.name
                    // Security: reject path-traversal attempts
                    if (name.contains("..") || name.startsWith("/")) {
                        error("Invalid path in zip: $name")
                    }

                    if (!entry.isDirectory) {
                        val bytes = zip.readBytes()
                        totalBytes += bytes.size
                        if (totalBytes > MAX_EXTRACT_BYTES) error("Zip extracts to more than 60 MB")
                        entries.add(name to bytes)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // Detect pet.json and spritesheet
            val petJsonEntry = entries.find { (name, _) ->
                name.endsWith("pet.json") && !name.contains("__MACOSX")
            } ?: error("No pet.json found in zip")

            val spritesheetEntry = entries.find { (name, _) ->
                name.endsWith("spritesheet.webp") || name.endsWith("spritesheet.png")
            } ?: error("No spritesheet found in zip")

            // Derive petId from the parent folder name in the zip
            val petId = petJsonEntry.first
                .split("/")
                .dropLast(1)
                .lastOrNull()
                ?.ifBlank { null }
                ?: "custom_pet"

            val sanitizedId = petId.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()

            // Extract to filesDir/pets/<petId>/
            val destDir = installedPetRepository.getInstalledDir(sanitizedId)
            destDir.mkdirs()

            File(destDir, "pet.json").writeBytes(petJsonEntry.second)

            val spritesheetName = if (spritesheetEntry.first.endsWith(".png"))
                "spritesheet.png" else "spritesheet.webp"
            File(destDir, spritesheetName).writeBytes(spritesheetEntry.second)

            installedPetRepository.setActivePetId(sanitizedId)
            sanitizedId
        }
    }
}
